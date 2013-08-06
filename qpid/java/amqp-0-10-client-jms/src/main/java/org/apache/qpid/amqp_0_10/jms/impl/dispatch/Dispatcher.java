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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.util.ConditionManager;

public class Dispatcher<K> implements Runnable
{
    private final AtomicBoolean _continue = new AtomicBoolean(true);

    private final AtomicBoolean _stopped = new AtomicBoolean(false);

    private final ConditionManager _dispatcherStarted = new ConditionManager(false);

    private final ConditionManager _dispatcherShutdown = new ConditionManager(false);

    private final LinkedBlockingQueue<Dispatchable<K>> _dispatchQueue = new LinkedBlockingQueue<Dispatchable<K>>();

    private Thread _thread;

    public Dispatcher()
    {
    }

    public void add(Dispatchable<K> dispatchable)
    {
        try
        {
            _dispatchQueue.put(dispatchable);
        }
        catch (InterruptedException e)
        {
            // TODO
        }
    }

    @Override
    public void run()
    {
        try
        {
            _dispatcherShutdown.setValueAndNotify(false);
            while (_continue.get())
            {
                if (_stopped.get())
                {
                    _dispatcherStarted.setValueAndNotify(false);
                }

                _dispatcherStarted.waitUntilTrue();

                if (!_stopped.get())
                {
                    try
                    {
                        _dispatchQueue.take().dispatch();
                    }
                    catch (InterruptedException e)
                    {
                        // continue
                    }
                }

            }
        }
        finally
        {
            _dispatcherShutdown.setValueAndNotify(true);
        }
    }

    public void interrupt()
    {
        _thread.interrupt();
    }

    public void signalDispatcherToShutdown()
    {
        _stopped.set(true);
        _continue.set(false);
        _dispatcherStarted.wakeUpAndReturn();
        interrupt();
    }

    public void waitForDispatcherToShutdown()
    {
        _dispatcherShutdown.waitUntilTrue();
    }

    public void signalDispatcherToStop()
    {
        _stopped.set(true);
        interrupt();
    }

    public void signalDispatcherToStart()
    {
        _stopped.set(false);
        _dispatcherStarted.setValueAndNotify(true);
    }

    public void waitForDispatcherToStop()
    {
        _dispatcherStarted.waitUntilFalse();
    }

    public boolean isDispatcherStopped()
    {
        return _stopped.get() && (!_dispatcherStarted.getCurrentValue());
    }

    public void drainQueue(K key)
    {
        Iterator<Dispatchable<K>> it = _dispatchQueue.iterator();

        while (it.hasNext())
        {
            Dispatchable<K> disp = it.next();
            if (disp.getKey() == key)
            {
                it.remove();
            }
        }
    }

    public void clearQueue()
    {
        _dispatchQueue.clear();
    }

    public void sort()
    {
        List<Dispatchable<K>> list = new ArrayList<Dispatchable<K>>(_dispatchQueue.size());
        _dispatchQueue.drainTo(list);
        Collections.sort(list, new DispatchableComparator<K>());
        _dispatchQueue.addAll(list);
    }

    public void setThread(Thread thread)
    {
        _thread = thread;
    }

    public Thread getThread()
    {
        return _thread;
    }
}