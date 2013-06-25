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

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

public class MessageProducerImpl implements MessageProducer
{

    @Override
    public void close() throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public int getDeliveryMode() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Destination getDestination() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean getDisableMessageID() throws JMSException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean getDisableMessageTimestamp() throws JMSException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int getPriority() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getTimeToLive() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void send(Message arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void send(Destination arg0, Message arg1) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void send(Message arg0, int arg1, int arg2, long arg3) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void send(Destination arg0, Message arg1, int arg2, int arg3, long arg4) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void setDeliveryMode(int arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void setDisableMessageID(boolean arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void setDisableMessageTimestamp(boolean arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void setPriority(int arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void setTimeToLive(long arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    public void closed()
    {
        // TODO Auto-generated method stub
        
    }

}
