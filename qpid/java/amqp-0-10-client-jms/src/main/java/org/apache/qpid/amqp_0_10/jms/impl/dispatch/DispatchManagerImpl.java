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

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;
import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.Session;

/**
 * Bare bones implementation of the DispatcherManager. A more intelligent and
 * efficient implementation could be done.
 * 
 */
public class DispatchManagerImpl implements DispatchManager<Session>
{
    private final int _dispatcherCount;

    private final ConnectionImpl _conn;

    private final Map<Session, Dispatcher<Session>> _dispatcherMap = new ConcurrentHashMap<Session, Dispatcher<Session>>();

    private final List<Dispatcher<Session>> _dispatchers;

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
            dispatcher.signalDispatcherToStop();
            dispatcher.waitForDispatcherToStop();
            dispatcher.drainQueue(key);
            dispatcher.signalDispatcherToStart();
        }
    }

    @Override
    public void dispatch(Dispatchable<Session> disp) throws NullPointerException
    {
        _dispatcherMap.get(disp).add(disp);
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
    public void stop()
    {
        for (Dispatcher<Session> dispatcher : _dispatchers)
        {
            dispatcher.signalDispatcherToStop();
            dispatcher.interrupt();
            dispatcher.waitForDispatcherToStop();
        }
    }

    @Override
    public void shutdown()
    {
        for (Dispatcher<Session> dispatcher : _dispatchers)
        {
            dispatcher.signalDispatcherToShutdown();
            dispatcher.interrupt();
            dispatcher.waitForDispatcherToShutdown();
            dispatcher.drainQueue();
        }
        _dispatcherMap.clear();
        _dispatchers.clear();

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