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

import java.util.concurrent.LinkedBlockingQueue;

import javax.jms.Destination;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

import org.apache.qpid.client.AMQDestination;

public class MessageConsumerImpl implements MessageConsumer
{   
    private SessionImpl _session;
    private DestinationImpl _dest;
    private String _selector;
    private boolean _noLocal;
    private String _subscriptionQueue;
    private int _capacity;
    
    private LinkedBlockingQueue _localQueue;
    
    protected MessageConsumerImpl(SessionImpl ssn, Destination dest, String selector, boolean noLocal) throws JMSException
    {
        if (!(dest instanceof DestinationImpl))
        {
            throw new InvalidDestinationException("Invalid destination class " + dest.getClass().getName());
        }
        _session = ssn;
        _dest = (DestinationImpl)dest;
        _selector = selector;
        _noLocal = noLocal;
        _capacity = AddressResolution.evaluateCapacity(0, _dest); // TODO get max prefetch from connection
        _localQueue = new LinkedBlockingQueue(_capacity);
    }

    @Override
    public MessageListener getMessageListener() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getMessageSelector() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Message receive() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Message receive(long arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Message receiveNoWait() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setMessageListener(MessageListener arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    public void commit()
    {
        // TODO Auto-generated method stub
        
    }

    public void rollback()
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void close() throws JMSException
    {
        // TODO Auto-generated method stub
    }

    public void closed()
    {
        // TODO Auto-generated method stub
        
    }
}
