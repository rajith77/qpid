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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import javax.jms.StreamMessage;

import org.apache.qpid.amqp_0_10.jms.impl.MessageImpl;
import org.apache.qpid.amqp_0_10.jms.impl.SessionImpl;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.transport.codec.BBEncoder;
import org.apache.qpid.util.ExceptionHelper;

public class StreamMessageImpl extends MessageImpl implements StreamMessage, ContentTypes
{
    private final static int LIST_MESSAGE_CAPACITY = Integer.getInteger(ClientProperties.QPID_LIST_MESSAGE_CAPACITY,
            ClientProperties.DEFAULT_LIST_MESSAGE_CAPACITY);

    private BBDecoder _decoder = null;

    private BBEncoder _encoder = null;

    private ByteBuffer _encodedBytes = null;

    private JMSException _exception = null;

    protected int _index = -1;

    protected List<Object> _list;

    StreamMessageImpl()
    {
        super();
        getMessageProperties().setContentType(AMQP_0_10_LIST);
        _list = new ArrayList<Object>(LIST_MESSAGE_CAPACITY);
    }

    StreamMessageImpl(SessionImpl ssn, int transferId, String consumerId, DeliveryProperties deliveryProps,
            MessageProperties msgProps, ByteBuffer data)
    {
        super(ssn, transferId, consumerId, deliveryProps, msgProps);
        try
        {
            if (propertyExists(PAYLOAD_NULL_PROPERTY) || data == null)
            {
                _list = null;
            }
            else if (data.remaining() == 0)
            {
                _list = Collections.emptyList();
            }
            else
            {
                data.rewind();
                _decoder = new BBDecoder();
                _decoder.init(data);
                _list = _decoder.readList();
                _encodedBytes = data;
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
        if (_exception != null)
        {
            throw _exception;
        }
        if (_encodedBytes == null)
        {
            if (_list.size() == 0)
            {
                _encodedBytes = EMPTY_BYTE_BUFFER;
            }
            else
            {
                try
                {
                    _encoder = new BBEncoder(1024);
                    _encoder.writeList(_list);
                    _encodedBytes = _encoder.segment();
                }
                catch (Exception e)
                {
                    throw ExceptionHelper.handleMessageException("Error encoding map message", e);
                }
            }
        }
        return _encodedBytes;
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _list.clear();
        _encodedBytes = null;
        _exception = null;
        _index = -1;
    }

    protected void checkPreConditionsForReading() throws JMSException
    {
        super.checkMessageReadable();
        if (_exception != null)
        {
            throw _exception;
        }
        _index++;

        if (_index >= _list.size())
        {
            throw new javax.jms.MessageEOFException("No more elements to read");
        }
    }

    @Override
    public boolean readBoolean() throws JMSException
    {
        checkPreConditionsForReading();
        Object o = _list.get(_index);

        if (o instanceof Boolean)
        {
            return ((Boolean) o).booleanValue();
        }
        else if (o instanceof String)
        {
            return Boolean.valueOf((String) o).booleanValue();
        }
        else if (o == null)
        {
            return Boolean.valueOf(null);
        }
        else
        {
            throw new MessageFormatException("Value is not a boolean. Value is of type : " + o.getClass());
        }
    }

    @Override
    public byte readByte() throws JMSException
    {
        checkPreConditionsForReading();
        Object o = _list.get(_index);

        if (o instanceof Byte)
        {
            return ((Byte) o).byteValue();
        }
        else if (o instanceof String)
        {
            return Byte.valueOf((String) o).byteValue();
        }
        else if (o == null)
        {
            return Byte.valueOf(null);
        }
        else
        {
            throw new MessageFormatException("Value is not a byte. Value is of type : " + o.getClass());
        }
    }

    @Override
    public int readBytes(byte[] array) throws JMSException
    {
        checkPreConditionsForReading();
        Object o = _list.get(_index);

        if (o == null)
        {
            return 0;
        }
        if ((o instanceof byte[]))
        {
            byte[] src = (byte[]) o;
            int read = Math.min(array.length, src.length);
            System.arraycopy(src, 0, array, 0, read);
            return read;
        }
        else
        {
            throw new MessageFormatException("Value is not a byte[]. Value is of type : " + o.getClass());
        }
    }

    @Override
    public char readChar() throws JMSException
    {
        checkPreConditionsForReading();
        Object o = _list.get(_index);

        if (o instanceof Character)
        {
            return ((Character) o).charValue();
        }
        else if (o == null)
        {
            throw new NullPointerException("The value is null, therefore cannot " + "be converted to char.");
        }
        else
        {
            throw new MessageFormatException("Value is not a char. Value is of type : " + o.getClass());
        }
    }

    @Override
    public double readDouble() throws JMSException
    {
        checkPreConditionsForReading();
        Object o = _list.get(_index);

        if (o instanceof Double)
        {
            return ((Double) o).doubleValue();
        }
        else if (o instanceof String)
        {
            return Double.valueOf((String) o);
        }
        else
        {
            try
            {
                _index--;
                return readFloat();
            }
            catch (MessageFormatException e)
            {
                throw new MessageFormatException("Value is not a double. Value is of type : " + o.getClass());
            }
        }
    }

    @Override
    public float readFloat() throws JMSException
    {
        checkPreConditionsForReading();
        Object o = _list.get(_index);

        if (o instanceof Float)
        {
            return ((Float) o).floatValue();
        }
        else if (o instanceof String)
        {
            return Float.valueOf((String) o).floatValue();
        }
        else if (o == null)
        {
            return Float.valueOf(null);
        }
        else
        {
            throw new MessageFormatException("Value is not a float. Value is of type : " + o.getClass());
        }
    }

    @Override
    public int readInt() throws JMSException
    {
        checkPreConditionsForReading();
        Object o = _list.get(_index);

        if (o instanceof Integer)
        {
            return ((Integer) o).intValue();
        }
        else if (o instanceof String)
        {
            return Integer.valueOf((String) o);
        }
        else
        {
            try
            {
                _index--;
                return readShort();
            }
            catch (MessageFormatException e)
            {
                throw new MessageFormatException("Value is not an int. Value is of type : " + o.getClass());
            }
        }
    }

    @Override
    public long readLong() throws JMSException
    {
        checkPreConditionsForReading();
        Object o = _list.get(_index);

        if (o instanceof Long)
        {
            return ((Long) o).longValue();
        }
        else if (o instanceof String)
        {
            return Long.valueOf((String) o);
        }
        else
        {
            try
            {
                _index--;
                return readInt();
            }
            catch (MessageFormatException e)
            {
                throw new MessageFormatException("Value is not a long. Value is of type : " + o.getClass());
            }
        }
    }

    @Override
    public Object readObject() throws JMSException
    {
        checkPreConditionsForReading();
        return _list.get(_index);
    }

    @Override
    public short readShort() throws JMSException
    {
        checkPreConditionsForReading();
        Object o = _list.get(_index);

        if (o instanceof Long)
        {
            return ((Short) o).shortValue();
        }
        else if (o instanceof String)
        {
            return Short.valueOf((String) o);
        }
        else
        {
            try
            {
                _index--;
                return readByte();
            }
            catch (MessageFormatException e)
            {
                throw new MessageFormatException("Value is not a short. Value is of type : " + o.getClass());
            }
        }
    }

    @Override
    public String readString() throws JMSException
    {
        checkPreConditionsForReading();
        Object value = _list.get(_index);
        if ((value instanceof String) || (value == null))
        {
            return (String) value;
        }
        else if (value instanceof byte[])
        {
            throw new MessageFormatException("Value is of type byte[], cannot be converted to String.");
        }
        else
        {
            return value.toString();
        }
    }

    @Override
    public void reset() throws JMSException
    {
        markReadOnly();
        _index = -1;
    }

    @Override
    public void writeBoolean(boolean value) throws JMSException
    {
        checkMessageWritable();
        _list.add(value);
    }

    @Override
    public void writeByte(byte value) throws JMSException
    {
        checkMessageWritable();
        _list.add(value);
    }

    @Override
    public void writeBytes(byte[] value) throws JMSException
    {
        checkMessageWritable();
        _list.add(value);
    }

    @Override
    public void writeBytes(byte[] value, int offset, int length) throws JMSException
    {
        checkMessageWritable();
        byte[] newBytes = new byte[length];
        System.arraycopy(value, offset, newBytes, 0, length);
        _list.add(value);
    }

    @Override
    public void writeChar(char value) throws JMSException
    {
        checkMessageWritable();
        _list.add(value);
    }

    @Override
    public void writeDouble(double value) throws JMSException
    {
        checkMessageWritable();
        _list.add(value);
    }

    @Override
    public void writeFloat(float value) throws JMSException
    {
        checkMessageWritable();
        _list.add(value);
    }

    @Override
    public void writeInt(int value) throws JMSException
    {
        checkMessageWritable();
        _list.add(value);
    }

    @Override
    public void writeLong(long value) throws JMSException
    {
        checkMessageWritable();
        _list.add(value);
    }

    @Override
    public void writeObject(Object value) throws JMSException
    {
        checkMessageWritable();
        if ((value instanceof Boolean) || (value instanceof Byte) || (value instanceof Short)
                || (value instanceof Integer) || (value instanceof Long) || (value instanceof Character)
                || (value instanceof Float) || (value instanceof Double) || (value instanceof String)
                || (value instanceof byte[]) || (value instanceof List) || (value instanceof Map)
                || (value instanceof UUID) || (value == null))
        {
            _list.add(value);
        }
        else
        {
            throw new MessageFormatException("Cannot write Object '" + value + "' of type "
                    + value.getClass().getName() + ", as it's not an allowed type");
        }
    }

    @Override
    public void writeShort(short value) throws JMSException
    {
        checkMessageWritable();
        _list.add(value);
    }

    @Override
    public void writeString(String value) throws JMSException
    {
        checkMessageWritable();
        _list.add(value);
    }

    @Override
    protected void bodyToString(StringBuffer buf) throws JMSException
    {
        buf.append("Body:{" + _list + "}");
    }
}