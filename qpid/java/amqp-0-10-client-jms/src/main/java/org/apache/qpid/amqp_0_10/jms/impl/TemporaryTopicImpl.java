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
package org.apache.qpid.amqp_0_10.jms.impl;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.JMSException;
import javax.jms.TemporaryTopic;

public class TemporaryTopicImpl extends TopicImpl implements TemporaryDestination, TemporaryTopic
{
    private final SessionImpl _session;

    private AtomicBoolean _deleted = new AtomicBoolean(false);

    public TemporaryTopicImpl(SessionImpl session) throws JMSException
    {
        super("amq.direct/Qpid_Temp_Topic_" + UUID.randomUUID());
        _session = session;
    }

    @Override
    public void delete() throws JMSException
    {
        _deleted.set(true);
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
}