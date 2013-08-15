/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.amqp_0_10.jms.impl.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;
import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.util.Logger;

/**
 * Bare bones implementation of the DispatcherManager. A more intelligent and
 * efficient implementation could be done. Not thread safe on it's own. Relies
 * on the ConnectionImpl for thread safety.
 * 
 */
public class DispatchManagerImpl implements DispatchManager<Session>
{
    private static final Logger _logger = Logger.get(DispatchManagerImpl.class);

    private final int _dispatcherCount;

    private final ConnectionImpl _conn;

    private final Map<Session, Dispatcher<Session>> _dispatcherMap = new ConcurrentHashMap<Session, Dispatcher<Session>>();

    private final List<Dispatcher<Session>> _dispatchers;

    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private int _nextDispatcherIndex = 0;

    public DispatchManagerImpl(ConnectionImpl conn)
    {
        _conn = conn;
        _dispatcherCount = _conn.getConfig().getDispatcherCount();
        _dispatchers = new ArrayList<Dispatcher<Session>>(_dispatcherCount);
        initDispatchers();
    }

    @Override
    public void register(Session key)
    {
        _dispatcherMap.put(key, getNextDispatcher());
    }

    @Override
    public void unregister(Session key)
    {
        Dispatcher<Session> dispatcher = _dispatcherMap.remove(key);
        if (dispatcher != null)
        {
            dispatcher.drainQueue(key);
        }
    }

    /**
     * Pre Condition : Message delivery for this session should be stopped
     * before invoking this method.
     */
    @Override
    public void requeue(Session key, Dispatchable<Session> dispatchable)
    {
        if (!_closed.get())
        {
            Dispatcher<Session> dispatcher = _dispatcherMap.get(key);
            if (dispatcher != null)
            {
                dispatcher.add(dispatchable);
            }
            else
            {
                _logger.warn("The session has been closed. Unable to requeue");
            }
        }
    }

    @Override
    public void sortDispatchQueue(Session key)
    {
        if (!_closed.get())
        {
            Dispatcher<Session> dispatcher = _dispatcherMap.get(key);
            if (dispatcher != null)
            {
                dispatcher.sort();
            }
            else
            {
                _logger.warn("The session has been closed. Unable to sort the dispatcher queue");
            }
        }
    }

    @Override
    public void dispatch(Dispatchable<Session> dispatchable) throws NullPointerException
    {
        if (!_closed.get())
        {
            Dispatcher<Session> dispatcher = _dispatcherMap.get(dispatchable.getKey());
            if (dispatcher != null)
            {
                dispatcher.add(dispatchable);
            }
            else
            {
                _logger.warn("The session has been closed. No session to dispatch message : " + dispatchable);
            }
        }
    }

    @Override
    public void clearDispatcherQueues()
    {
        for (Dispatcher<Session> dispatcher : _dispatchers)
        {
            dispatcher.clearQueue();
        }
    }

    @Override
    public void startDispatcher(Session key)
    {
        if (!_closed.get())
        {
            Dispatcher<Session> dispatcher = _dispatcherMap.get(key);
            if (dispatcher != null)
            {
                dispatcher.signalDispatcherToStart();
            }
        }
    }

    @Override
    public void start()
    {
        for (Dispatcher<Session> dispatcher : _dispatchers)
        {
            dispatcher.signalDispatcherToStart();
        }
    }

    @Override
    public void stopDispatcher(Session key)
    {
        if (!_closed.get())
        {
            Dispatcher<Session> dispatcher = _dispatcherMap.get(key);
            if (dispatcher != null)
            {
                if (!dispatcher.isDispatcherStopped())
                {
                    dispatcher.signalDispatcherToStop();
                    dispatcher.waitForDispatcherToStop();
                }
            }
        }
    }

    @Override
    public void stop()
    {
        if (!_closed.get())
        {
            for (Dispatcher<Session> dispatcher : _dispatchers)
            {
                if (!dispatcher.isDispatcherStopped())
                {
                    dispatcher.signalDispatcherToStop();
                    dispatcher.waitForDispatcherToStop();
                }
            }
        }
    }

    /** Marks as stopped, but doesn't wait for it to stop **/
    @Override
    public void markStopped()
    {
        if (!_closed.get())
        {
            for (Dispatcher<Session> dispatcher : _dispatchers)
            {
                if (!dispatcher.isDispatcherStopped())
                {
                    dispatcher.signalDispatcherToStop();
                }
            }
        }
    }

    @Override
    public void shutdown()
    {
        if (!_closed.get())
        {
            _closed.set(true);
            for (Dispatcher<Session> dispatcher : _dispatchers)
            {
                dispatcher.signalDispatcherToShutdown();
                dispatcher.interrupt();
                dispatcher.waitForDispatcherToShutdown();
                dispatcher.clearQueue();
            }
            _dispatcherMap.clear();
            _dispatchers.clear();
        }
    }

    private void initDispatchers()
    {
        for (int i = 0; i < _dispatcherCount; i++)
        {
            Dispatcher<Session> dispatcher = new Dispatcher<Session>();
            String name = "Dispatcher-" + _dispatcherCount + "-Conn-" + _conn.getConnectionId();

            try
            {
                Thread thread = Threading.getThreadFactory().createThread(dispatcher);
                thread.setName(name);
                thread.start();
                dispatcher.setThread(thread);
            }
            catch (Exception e)
            {
                throw new Error("Error creating Dispatcher thread", e);
            }

            _dispatchers.add(dispatcher);
        }
    }

    private Dispatcher<Session> getNextDispatcher()
    {
        Dispatcher<Session> dispatcher = _dispatchers.get(_nextDispatcherIndex);

        _nextDispatcherIndex++;

        if (_nextDispatcherIndex >= _dispatchers.size())
        {
            _nextDispatcherIndex = 0;
        }

        return dispatcher;
    }
}