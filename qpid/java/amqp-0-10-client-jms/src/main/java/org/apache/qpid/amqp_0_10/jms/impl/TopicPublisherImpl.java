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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Topic;
import javax.jms.TopicPublisher;

public class TopicPublisherImpl extends MessageProducerImpl implements TopicPublisher
{

    public TopicPublisherImpl(SessionImpl ssn, TopicImpl topic) throws JMSException
    {
        super(ssn, topic);
    }

    @Override
    public Topic getTopic() throws JMSException
    {
        return (TopicImpl) getDestination();
    }

    @Override
    public void publish(Message msg) throws JMSException
    {
        send(msg);
    }

    @Override
    public void publish(Topic topic, Message msg) throws JMSException
    {
        send(topic, msg);
    }

    @Override
    public void publish(Message msg, int deliveryMode, int priority, long timeToLive) throws JMSException
    {
        send(msg, deliveryMode, priority, timeToLive);
    }

    @Override
    public void publish(Topic topic, Message msg, int deliveryMode, int priority, long timeToLive) throws JMSException
    {
        send(topic, msg, deliveryMode, priority, timeToLive);
    }
}
