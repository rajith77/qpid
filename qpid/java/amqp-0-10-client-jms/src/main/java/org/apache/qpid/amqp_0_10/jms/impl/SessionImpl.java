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

import java.io.Serializable;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

public class SessionImpl implements Session, QueueSession, TopicSession
{

    @Override
    public TopicPublisher createPublisher(Topic arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicSubscriber createSubscriber(Topic arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicSubscriber createSubscriber(Topic arg0, String arg1,
            boolean arg2) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public QueueReceiver createReceiver(Queue arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public QueueReceiver createReceiver(Queue arg0, String arg1)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public QueueSender createSender(Queue arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void commit() throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public QueueBrowser createBrowser(Queue arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public QueueBrowser createBrowser(Queue arg0, String arg1)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public MessageConsumer createConsumer(Destination arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public MessageConsumer createConsumer(Destination arg0, String arg1)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public MessageConsumer createConsumer(Destination arg0, String arg1,
            boolean arg2) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic arg0, String arg1)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic arg0, String arg1,
            String arg2, boolean arg3) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public MapMessage createMapMessage() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Message createMessage() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable arg0)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public MessageProducer createProducer(Destination arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Queue createQueue(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TextMessage createTextMessage() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TextMessage createTextMessage(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Topic createTopic(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getAcknowledgeMode() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public MessageListener getMessageListener() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean getTransacted() throws JMSException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void recover() throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void rollback() throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void run()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void setMessageListener(MessageListener arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void unsubscribe(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    public void stop()
    {
        // TODO Auto-generated method stub
        
    }

    public void start()
    {
        // TODO Auto-generated method stub
        
    }

}
