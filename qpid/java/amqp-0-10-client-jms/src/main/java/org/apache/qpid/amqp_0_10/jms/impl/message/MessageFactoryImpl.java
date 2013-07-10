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

import javax.jms.BytesMessage;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.apache.qpid.amqp_0_10.jms.MessageFactory;
import org.apache.qpid.amqp_0_10.jms.impl.SessionImpl;
import org.apache.qpid.jms.ListMessage;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;

public class MessageFactoryImpl implements MessageFactory, ContentTypes
{
    final Map<String, MessageType> _contentTypeToMsgTypeMap = new HashMap<String, MessageType>();

    MessageFactoryImpl()
    {
        _contentTypeToMsgTypeMap.put(BINARY, MessageType.BINARY);
        _contentTypeToMsgTypeMap.put(TEXT_PLAIN, MessageType.STRING);
        _contentTypeToMsgTypeMap.put(TEXT_XML, MessageType.STRING);
        _contentTypeToMsgTypeMap.put(AMQP_MAP, MessageType.MAP);
        _contentTypeToMsgTypeMap.put(AMQP_0_10_MAP, MessageType.MAP);
        _contentTypeToMsgTypeMap.put(AMQP_LIST, MessageType.LIST);
        _contentTypeToMsgTypeMap.put(AMQP_0_10_LIST, MessageType.LIST);
        _contentTypeToMsgTypeMap.put(JAVA_OBJECT, MessageType.JAVA_OBJECT);
    }

    @Override
    public Message createMessage()
    {
        return new BytesMessageImpl();
    }

    @Override
    public BytesMessage createBytesMessage()
    {
        return new BytesMessageImpl();
    }

    @Override
    public TextMessage createTextMessage()
    {
        return new TextMessageImpl();
    }

    @Override
    public MapMessage createMapMessage()
    {
        return new MapMessageImpl();
    }

    @Override
    public ObjectMessage createObjectMessage()
    {
        return new ObjectMessageImpl();
    }

    @Override
    public StreamMessage createStreamMessage()
    {
        return new ListMessageImpl();
    }

    @Override
    public ListMessage createListMessage()
    {
        return new ListMessageImpl();
    }

    @Override
    public Message createMessage(Session ssn, int transferId, DeliveryProperties deliveryProps,
            MessageProperties msgProps, ByteBuffer data)
    {
        MessageType type = MessageType.BINARY;
        if (msgProps != null)
        {
            if (_contentTypeToMsgTypeMap.containsKey(msgProps.getContentType()))
            {
                type = _contentTypeToMsgTypeMap.get(msgProps.getContentType());
            }
        }

        switch (type)
        {
        case STRING:
            return new TextMessageImpl((SessionImpl) ssn, transferId, deliveryProps, msgProps, data);
        case MAP:
            return new MapMessageImpl((SessionImpl) ssn, transferId, deliveryProps, msgProps, data);
        case LIST:
            return new ListMessageImpl((SessionImpl) ssn, transferId, deliveryProps, msgProps, data);
        case JAVA_OBJECT:
            return new ObjectMessageImpl((SessionImpl) ssn, transferId, deliveryProps, msgProps, data);
        default:
            return new BytesMessageImpl((SessionImpl) ssn, transferId, deliveryProps, msgProps, data);
        }
    }

    @Override
    public void registerContentType(String contentType, MessageType type)
    {
        _contentTypeToMsgTypeMap.put(contentType, type);
    }
}