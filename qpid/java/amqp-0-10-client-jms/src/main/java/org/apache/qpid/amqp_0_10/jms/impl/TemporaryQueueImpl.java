/*
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
 */
package org.apache.qpid.amqp_0_10.jms.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.IllegalStateException;
import javax.jms.JMSException;

public class TemporaryQueueImpl extends QueueImpl implements TemporaryQueue
{
    private final String _name;

    private final SessionImpl _session;

    private AtomicBoolean _deleted = new AtomicBoolean(false);

    private final Set<MessageConsumerImpl> _consumers = Collections.synchronizedSet(new HashSet<MessageConsumerImpl>());

    public TemporaryQueueImpl(SessionImpl session) throws JMSException
    {
        super("Qpid_Temp_Queue_" + UUID.randomUUID() + ";{create:always}");
        _name = getAddress().getName();
        _session = session;
    }

    @Override
    public void delete() throws JMSException
    {
        if (!_deleted.get())
        {
            if (_consumers.isEmpty())
            {
                deleteQueue(true);
            }
            else
            {
                throw new IllegalStateException("Cannot delete destination as it has consumers");
            }
        }
    }

    @Override
    public void deleteQueue(boolean unregister) throws JMSException
    {
        _deleted.set(true);
        if (unregister)
        {
            _session.removeTempQueue(this);
        }
        _session.getAMQPSession().queueDelete(_name);
    }

    @Override
    public String getQueueName()
    {
        return _name;
    }

    @Override
    public boolean isDeleted()
    {
        return _deleted.get();
    }

    @Override
    public SessionImpl getSession()
    {
        return _session;
    }

    @Override
    public void addConsumer(MessageConsumerImpl cons)
    {
        _consumers.add(cons);
    }

    @Override
    public void removeConsumer(MessageConsumerImpl cons)
    {
        _consumers.remove(cons);
    }
}
