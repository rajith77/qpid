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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.StreamMessage;

import org.apache.qpid.amqp_0_10.jms.impl.MessageImpl;
import org.apache.qpid.amqp_0_10.jms.impl.SessionImpl;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.jms.ListMessage;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.transport.codec.BBEncoder;
import org.apache.qpid.util.ExceptionHelper;

public class StreamMessageImpl extends MessageImpl implements StreamMessage
{
    private final static int LIST_MESSAGE_CAPACITY = Integer.getInteger(ClientProperties.QPID_LIST_MESSAGE_CAPACITY,
            ClientProperties.DEFAULT_LIST_MESSAGE_CAPACITY);
    private BBDecoder _decoder = null;
    private BBEncoder _encoder = null;
    private ByteBuffer _encodedValue = null;
    private JMSException _exception = null;
    private int _index = -1;
    private List<Object> _map;

    StreamMessageImpl() throws JMSException
    {
        super();
        getMessageProperties().setContentType("amqp-0-10/list");
        _map = new ArrayList<Object>(LIST_MESSAGE_CAPACITY);
    }

    StreamMessageImpl(SessionImpl ssn, int transferId, DeliveryProperties deliveryProps, MessageProperties msgProps,
            ByteBuffer data)
    {
        super(ssn, transferId, deliveryProps, msgProps);
        try
        {
            if (propertyExists(PAYLOAD_NULL_PROPERTY) || data == null)
            {
                _map = null;
            }
            else if (data.remaining() == 0)
            {
                _map = Collections.emptyList();
            }
            else
            {
                data.rewind();
                _decoder = new BBDecoder();
                _decoder.init(data);
                _map = _decoder.readList();
            }
        }
        catch (JMSException e)
        {
            _exception = e;
        }
        catch (Exception e)
        {
            _exception = ExceptionHelper.handleMessageException("Error decoding map message", e);
        }
    }

    @Override
    public ByteBuffer getContent() throws JMSException
    {
        if (_encodedValue == null)
        {
            if (_map.size() == 0)
            {
                _encodedValue = EMPTY_BYTE_BUFFER;
            }
            else
            {
                try
                {
                    _encoder = new BBEncoder(1024);
                    _encoder.writeList(_map);
                    _encodedValue = _encoder.segment();
                }
                catch (Exception e)
                {
                    throw ExceptionHelper.handleMessageException("Error encoding map message", e);
                }
            }
        }
        return _encodedValue;
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _map.clear();
        _encodedValue = null;
        _exception = null;
        _index = -1;
    }

    @Override
    protected void isContentReadable() throws JMSException
    {
        super.isContentReadable();
        if (_exception != null)
        {
            throw _exception;
        }
    }

    @Override
    public boolean readBoolean() throws JMSException
    {
        
    }

    @Override
    public byte readByte() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int readBytes(byte[] arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public char readChar() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double readDouble() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float readFloat() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int readInt() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long readLong() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Object readObject() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public short readShort() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String readString() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void reset() throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeBoolean(boolean arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeByte(byte arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeBytes(byte[] arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeBytes(byte[] arg0, int arg1, int arg2) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeChar(char arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeDouble(double arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeFloat(float arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeInt(int arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeLong(long arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeObject(Object arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeShort(short arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeString(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub

    }
}
