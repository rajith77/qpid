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
package org.apache.qpid.amqp_0_10.jms.impl.message;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.apache.qpid.amqp_0_10.jms.MessageFactory;
import org.apache.qpid.jms.ListMessage;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;

public class MessageFactoryImpl implements MessageFactory
{
    final Map<String, MessageType> _contentTypeToMsgTypeMap = new HashMap<String, MessageType>();
    
    MessageFactoryImpl()
    {
        _contentTypeToMsgTypeMap.put("text/plain", MessageType.STRING);
        _contentTypeToMsgTypeMap.put("text/xml", MessageType.STRING);
        _contentTypeToMsgTypeMap.put("amqp/map", MessageType.MAP);
        _contentTypeToMsgTypeMap.put("amqp-0-10/map", MessageType.MAP);
        _contentTypeToMsgTypeMap.put("amqp/list", MessageType.LIST);
        _contentTypeToMsgTypeMap.put("amqp-0-10/list", MessageType.LIST);
        _contentTypeToMsgTypeMap.put("application/octet-stream", MessageType.BINARY);
    }

    @Override
    public Message createMessage(Session ssn)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TextMessage createTextMessage(Session ssn)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public MapMessage createMapMessage(Session ssn)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ObjectMessage createObjectMessage(Session ssn)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public StreamMessage createStreamMessage(Session ssn)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ListMessage createListMessage(Session ssn)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Message createMessage(Session ssn, DeliveryProperties deliveryProps, MessageProperties msgProps,
            ByteBuffer data)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void registerContentType(String contentType, MessageType type)
    {
        _contentTypeToMsgTypeMap.put(contentType, type);
    }

}
