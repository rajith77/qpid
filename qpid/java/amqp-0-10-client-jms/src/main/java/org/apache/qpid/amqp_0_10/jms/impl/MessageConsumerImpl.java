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
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

public class MessageConsumerImpl implements MessageConsumer
{
    private SessionImpl _session;
    private DestinationImpl _dest;
    private String _selector;
    private boolean _noLocal;
    
    protected MessageConsumerImpl(SessionImpl ssn, DestinationImpl dest, String selector, boolean noLocal)
    {
        _session = ssn;
        _dest = dest;
        _selector = selector;
        _noLocal = noLocal;
        
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
