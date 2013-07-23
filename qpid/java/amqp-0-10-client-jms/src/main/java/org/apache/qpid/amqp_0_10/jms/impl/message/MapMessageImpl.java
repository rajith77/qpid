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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageFormatException;

import org.apache.qpid.amqp_0_10.jms.impl.MessageImpl;
import org.apache.qpid.amqp_0_10.jms.impl.SessionImpl;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.transport.codec.BBEncoder;
import org.apache.qpid.util.ExceptionHelper;

public class MapMessageImpl extends MessageImpl implements MapMessage, ContentTypes
{
    private final static int MAP_MESSAGE_CAPACITY = Integer.getInteger(ClientProperties.QPID_MAP_MESSAGE_CAPACITY,
            ClientProperties.DEFAULT_MAP_MESSAGE_CAPACITY);

    private BBDecoder _decoder = null;

    private BBEncoder _encoder = null;

    private ByteBuffer _encodedBytes = null;

    private JMSException _exception = null;

    private Map<String, Object> _map;

    MapMessageImpl()
    {
        super();
        getMessageProperties().setContentType(AMQP_0_10_MAP);
        _map = new HashMap<String, Object>(MAP_MESSAGE_CAPACITY);
    }

    MapMessageImpl(SessionImpl ssn, int transferId, String consumerId, DeliveryProperties deliveryProps,
            MessageProperties msgProps, ByteBuffer data)
    {
        super(ssn, transferId, consumerId, deliveryProps, msgProps);
        try
        {
            if (propertyExists(PAYLOAD_NULL_PROPERTY) || data == null)
            {
                _map = null;
            }
            else if (data.remaining() == 0)
            {
                _map = Collections.emptyMap();
            }
            else
            {
                data.rewind();
                _decoder = new BBDecoder();
                _decoder.init(data);
                _map = _decoder.readMap();
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
            if (_map.size() == 0)
            {
                _encodedBytes = EMPTY_BYTE_BUFFER;
            }
            else
            {
                try
                {
                    _encoder = new BBEncoder(1024);
                    _encoder.writeMap(_map);
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
        _map.clear();
        _encodedBytes = null;
        _exception = null;
    }

    protected void checkPreConditionsForReading() throws JMSException
    {
        if (_exception != null)
        {
            throw _exception;
        }
    }

    @Override
    public boolean getBoolean(String name) throws JMSException
    {
        checkPreConditionsForReading();
        checkPropertyName(name);
        Object o = _map.get(name);

        if (o instanceof Boolean)
        {
            return ((Boolean) o).booleanValue();
        }
        else if (o instanceof String)
        {
            return Boolean.valueOf((String) o).booleanValue();
        }
        else if (_map.containsKey(name))
        {
            throw new MessageFormatException("getBoolean(\"" + name + "\") failed as value is not boolean: " + o);
        }
        else
        {
            return Boolean.valueOf(null);
        }
    }

    @Override
    public byte getByte(String name) throws JMSException
    {
        checkPreConditionsForReading();
        checkPropertyName(name);
        Object o = _map.get(name);

        if (o instanceof Byte)
        {
            return ((Byte) o).byteValue();
        }
        else if (o instanceof String)
        {
            return Byte.valueOf((String) o).byteValue();
        }
        else if (_map.containsKey(name))
        {
            throw new MessageFormatException("getByte(\"" + name + "\") failed as value is not a byte: " + o);
        }
        else
        {
            return Byte.valueOf(null);
        }
    }

    @Override
    public byte[] getBytes(String name) throws JMSException
    {
        checkPreConditionsForReading();
        checkPropertyName(name);
        Object o = _map.get(name);

        if (!_map.containsKey(name))
        {
            throw new MessageFormatException("Property '" + name + "' not present");
        }
        else if ((o instanceof byte[]) || (o == null))
        {
            return (byte[]) o;
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + o.getClass().getName()
                    + " cannot be converted to byte[].");
        }
    }

    @Override
    public char getChar(String name) throws JMSException
    {
        checkPreConditionsForReading();
        checkPropertyName(name);
        Object o = _map.get(name);

        if (!_map.containsKey(name))
        {
            throw new MessageFormatException("Property " + name + " not present");
        }
        else if (o instanceof Character)
        {
            return ((Character) o).charValue();
        }
        else if (o == null)
        {
            throw new NullPointerException("Property " + name + " has null value and therefore cannot "
                    + "be converted to char.");
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + o.getClass().getName()
                    + " cannot be converted to char.");
        }
    }

    @Override
    public double getDouble(String name) throws JMSException
    {
        checkPreConditionsForReading();
        checkPropertyName(name);
        Object o = _map.get(name);

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
                return Double.valueOf(getFloatProperty(name));
            }
            catch (MessageFormatException e)
            {
                throw new MessageFormatException("getDouble(\"" + name + "\") failed as value is not a double: " + o);
            }
        }
    }

    @Override
    public float getFloat(String name) throws JMSException
    {
        checkPreConditionsForReading();
        checkPropertyName(name);
        Object o = _map.get(name);

        if (o instanceof Float)
        {
            return ((Float) o).floatValue();
        }
        else if (o instanceof String)
        {
            return Float.valueOf((String) o).floatValue();
        }
        else if (_map.containsKey(name))
        {
            throw new MessageFormatException("getFloat(\"" + name + "\") failed as value is not a float: " + o);
        }
        else
        {
            throw new NullPointerException("No such property: " + name);
        }
    }

    @Override
    public int getInt(String name) throws JMSException
    {
        checkPreConditionsForReading();
        checkPropertyName(name);
        Object o = _map.get(name);

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
                return Integer.valueOf(getShortProperty(name));
            }
            catch (MessageFormatException e)
            {
                throw new MessageFormatException("getInt(\"" + name + "\") failed as value is not an int: " + o);
            }

        }
    }

    @Override
    public long getLong(String name) throws JMSException
    {
        checkPreConditionsForReading();
        checkPropertyName(name);
        Object o = _map.get(name);

        if (o instanceof Long)
        {
            return ((Long) o).longValue();
        }
        else if (o instanceof Integer)
        {
            return ((Integer) o).longValue();
        }

        if (o instanceof Short)
        {
            return ((Short) o).longValue();
        }

        if (o instanceof Byte)
        {
            return ((Byte) o).longValue();
        }
        else if ((o instanceof String) || (o == null))
        {
            return Long.valueOf((String) o).longValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + o.getClass().getName()
                    + " cannot be converted to long.");
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Enumeration getMapNames() throws JMSException
    {
        return Collections.enumeration(_map.keySet());
    }

    @Override
    public Object getObject(String name) throws JMSException
    {
        checkPreConditionsForReading();
        checkPropertyName(name);
        return _map.get(name);
    }

    @Override
    public short getShort(String name) throws JMSException
    {
        checkPreConditionsForReading();
        checkPropertyName(name);
        Object o = _map.get(name);

        if (o instanceof Short)
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
                return Short.valueOf(getByteProperty(name));
            }
            catch (MessageFormatException e)
            {
                throw new MessageFormatException("getShort(\"" + name + "\") failed as value is not a short: " + o);
            }
        }
    }

    @Override
    public String getString(String name) throws JMSException
    {
        Object value = _map.get(name);

        if ((value instanceof String) || (value == null))
        {
            return (String) value;
        }
        else if (value instanceof byte[])
        {
            throw new MessageFormatException("Property " + name + " of type byte[] " + "cannot be converted to String.");
        }
        else
        {
            return value.toString();
        }
    }

    @Override
    public boolean itemExists(String name) throws JMSException
    {
        checkPreConditionsForReading();
        checkPropertyName(name);
        return _map.containsKey(name);
    }

    @Override
    public void setBoolean(String name, boolean value) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(name);
        _map.put(name, value);
    }

    @Override
    public void setByte(String name, byte value) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(name);
        _map.put(name, value);
    }

    @Override
    public void setBytes(String name, byte[] value) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(name);
        _map.put(name, value);
    }

    @Override
    public void setBytes(String name, byte[] value, int offset, int length) throws JMSException
    {
        if ((offset == 0) && (length == value.length))
        {
            setBytes(name, value);
        }
        else
        {
            byte[] newBytes = new byte[length];
            System.arraycopy(value, offset, newBytes, 0, length);
            setBytes(name, newBytes);
        }
    }

    @Override
    public void setChar(String name, char value) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(name);
        _map.put(name, value);
    }

    @Override
    public void setDouble(String name, double value) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(name);
        _map.put(name, value);
    }

    @Override
    public void setFloat(String name, float value) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(name);
        _map.put(name, value);
    }

    @Override
    public void setInt(String name, int value) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(name);
        _map.put(name, value);
    }

    @Override
    public void setLong(String name, long value) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(name);
        _map.put(name, value);
    }

    @Override
    public void setShort(String name, short value) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(name);
        _map.put(name, value);
    }

    @Override
    public void setString(String name, String value) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(name);
        _map.put(name, value);
    }

    @Override
    public void setObject(String propName, Object value) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(propName);
        if ((value instanceof Boolean) || (value instanceof Byte) || (value instanceof Short)
                || (value instanceof Integer) || (value instanceof Long) || (value instanceof Character)
                || (value instanceof Float) || (value instanceof Double) || (value instanceof String)
                || (value instanceof byte[]) || (value instanceof List) || (value instanceof Map)
                || (value instanceof UUID) || (value == null))
        {
            _map.put(propName, value);
        }
        else
        {
            throw new MessageFormatException("Cannot set property " + propName + " to value " + value + "of type "
                    + value.getClass().getName() + ", as it's not an allowed type");
        }
    }

    // for testing
    void setMap(Map<String, Object> map)
    {
        _map = map;
    }

    // for testing
    Map<String, Object> getMap()
    {
        return _map;
    }

    @Override
    protected void bodyToString(StringBuffer buf) throws JMSException
    {
        buf.append("Body:{" + _map + "}");
    }
}