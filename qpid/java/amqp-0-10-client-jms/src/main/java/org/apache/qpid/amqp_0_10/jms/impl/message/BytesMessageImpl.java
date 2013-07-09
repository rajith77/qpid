/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.amqp_0_10.jms.impl.message;

import java.io.EOFException;
import java.nio.ByteBuffer;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;

import org.apache.qpid.amqp_0_10.jms.impl.MessageImpl;
import org.apache.qpid.amqp_0_10.jms.impl.SessionImpl;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.typedmessage.TypedBytesContentReader;
import org.apache.qpid.typedmessage.TypedBytesContentWriter;
import org.apache.qpid.util.ExceptionHelper;

public class BytesMessageImpl extends MessageImpl implements BytesMessage
{
    private TypedBytesContentReader _typedBytesContentReader;

    private TypedBytesContentWriter _typedBytesContentWriter;

    BytesMessageImpl()
    {
        super();
        _typedBytesContentWriter = new TypedBytesContentWriter();
    }

    BytesMessageImpl(SessionImpl ssn, int transferId, DeliveryProperties deliveryProps,
            MessageProperties msgProps, ByteBuffer data)
    {
        super(ssn, transferId, deliveryProps, msgProps);
        _typedBytesContentReader = new TypedBytesContentReader(data);
    }

    @Override
    public ByteBuffer getContent() throws JMSException
    {
        if (getContentReadWriteMode() == Mode.READABLE)
        {
            return _typedBytesContentReader.getData();
        }
        else
        {
            return _typedBytesContentWriter.getData();
        }
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _typedBytesContentReader = null;
        _typedBytesContentWriter = new TypedBytesContentWriter();

    }

    @Override
    public long getBodyLength() throws JMSException
    {
        isContentReadable();
        return _typedBytesContentReader.size();
    }

    @Override
    public boolean readBoolean() throws JMSException
    {
        isContentReadable();
        try
        {
            checkAvailable(1);
            return _typedBytesContentReader.readBooleanImpl();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error reading boolean", e);
        }
    }

    @Override
    public byte readByte() throws JMSException
    {
        isContentReadable();
        try
        {
            checkAvailable(1);
            return _typedBytesContentReader.readByteImpl();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error reading byte", e);
        }
    }

    @Override
    public int readUnsignedByte() throws JMSException
    {
        isContentReadable();
        try
        {
            checkAvailable(1);
            return _typedBytesContentReader.readByteImpl() & 0xFF;
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error reading unsigned byte", e);
        }
    }

    @Override
    public short readShort() throws JMSException
    {
        isContentReadable();
        try
        {
            checkAvailable(2);
            return _typedBytesContentReader.readShortImpl();
        }

        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error reading short", e);
        }
    }

    @Override
    public int readUnsignedShort() throws JMSException
    {
        isContentReadable();
        try
        {
            checkAvailable(2);
            return _typedBytesContentReader.readShortImpl() & 0xFFFF;
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error reading unsigned short", e);
        }
    }

    /**
     * Note that this method reads a unicode character as two bytes from the
     * stream
     * 
     * @return the character read from the stream
     * @throws JMSException
     */
    @Override
    public char readChar() throws JMSException
    {
        isContentReadable();
        try
        {
            checkAvailable(2);
            return _typedBytesContentReader.readCharImpl();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error reading char", e);
        }
    }

    @Override
    public int readInt() throws JMSException
    {
        isContentReadable();
        try
        {
            checkAvailable(4);
            return _typedBytesContentReader.readIntImpl();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error reading int", e);
        }
    }

    @Override
    public long readLong() throws JMSException
    {
        isContentReadable();
        try
        {
            checkAvailable(8);
            return _typedBytesContentReader.readLongImpl();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error reading long", e);
        }
    }

    @Override
    public float readFloat() throws JMSException
    {
        isContentReadable();
        try
        {
            checkAvailable(4);
            return _typedBytesContentReader.readFloatImpl();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error reading float", e);
        }
    }

    @Override
    public double readDouble() throws JMSException
    {
        isContentReadable();
        try
        {
            checkAvailable(8);
            return _typedBytesContentReader.readDoubleImpl();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error reading double", e);
        }
    }

    @Override
    public String readUTF() throws JMSException
    {
        isContentReadable();
        // we check only for one byte since theoretically the string could be
        // only a single byte when using UTF-8 encoding

        try
        {
            return _typedBytesContentReader.readLengthPrefixedUTF();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error reading UTF String", e);
        }
    }

    @Override
    public int readBytes(byte[] bytes) throws JMSException
    {
        if (bytes == null)
        {
            throw new JMSException("byte array must not be null");
        }
        isContentReadable();
        int count = (_typedBytesContentReader.remaining() >= bytes.length ? bytes.length : _typedBytesContentReader
                .remaining());
        if (count == 0)
        {
            return -1;
        }
        else
        {
            _typedBytesContentReader.readRawBytes(bytes, 0, count);
            return count;
        }
    }

    @Override
    public int readBytes(byte[] bytes, int maxLength) throws JMSException
    {
        if (bytes == null)
        {
            throw new JMSException("byte array must not be null");
        }
        if (maxLength > bytes.length)
        {
            throw new JMSException("maxLength must be <= bytes.length");
        }
        isContentReadable();
        int count = (_typedBytesContentReader.remaining() >= maxLength ? maxLength : _typedBytesContentReader
                .remaining());
        if (count == 0)
        {
            return -1;
        }
        else
        {
            _typedBytesContentReader.readRawBytes(bytes, 0, count);
            return count;
        }
    }

    @Override
    public void reset() throws JMSException
    {
        setContentReadWriteMode(Mode.READABLE);

        if (_typedBytesContentReader != null)
        {
            _typedBytesContentReader.reset();
        }
        else if (_typedBytesContentWriter != null)
        {
            _typedBytesContentReader = new TypedBytesContentReader(_typedBytesContentWriter.getData());
        }
    }

    @Override
    public void writeBoolean(boolean b) throws JMSException
    {
        isContentWritable();
        try
        {
            _typedBytesContentWriter.writeBooleanImpl(b);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error writing boolean", e);
        }
    }

    @Override
    public void writeByte(byte b) throws JMSException
    {
        isContentWritable();
        try
        {
            _typedBytesContentWriter.writeByteImpl(b);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error writing byte", e);
        }
    }

    @Override
    public void writeShort(short i) throws JMSException
    {
        isContentWritable();
        try
        {
            _typedBytesContentWriter.writeShortImpl(i);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error writing short", e);
        }
    }

    @Override
    public void writeChar(char c) throws JMSException
    {
        isContentWritable();
        try
        {
            _typedBytesContentWriter.writeCharImpl(c);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error writing char", e);
        }
    }

    @Override
    public void writeInt(int i) throws JMSException
    {
        isContentWritable();
        try
        {
            _typedBytesContentWriter.writeIntImpl(i);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error writing int", e);
        }
    }

    @Override
    public void writeLong(long l) throws JMSException
    {
        isContentWritable();
        try
        {
            _typedBytesContentWriter.writeLongImpl(l);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error writing long", e);
        }
    }

    @Override
    public void writeFloat(float v) throws JMSException
    {
        isContentWritable();
        try
        {
            _typedBytesContentWriter.writeFloatImpl(v);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error writing float", e);
        }
    }

    @Override
    public void writeDouble(double v) throws JMSException
    {
        isContentWritable();
        try
        {
            _typedBytesContentWriter.writeDoubleImpl(v);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error writing double", e);
        }
    }

    @Override
    public void writeUTF(String string) throws JMSException
    {
        isContentWritable();
        try
        {
            _typedBytesContentWriter.writeLengthPrefixedUTF(string);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error writing UTF String", e);
        }
    }

    @Override
    public void writeBytes(byte[] bytes) throws JMSException
    {
        isContentWritable();
        try
        {
            writeBytes(bytes, 0, bytes.length);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error writing bytes", e);
        }
    }

    @Override
    public void writeBytes(byte[] bytes, int offset, int length) throws JMSException
    {
        isContentWritable();
        try
        {
            _typedBytesContentWriter.writeBytesRaw(bytes, offset, length);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.handleMessageException("Error writing bytes", e);
        }
    }

    @Override
    public void writeObject(Object object) throws JMSException
    {
        isContentWritable();
        if (object == null)
        {
            throw new JMSException("Argument must not be null");
        }
        Class<?> clazz = object.getClass();
        if (clazz == Byte.class)
        {
            writeByte((Byte) object);
        }
        else if (clazz == Boolean.class)
        {
            writeBoolean((Boolean) object);
        }
        else if (clazz == byte[].class)
        {
            writeBytes((byte[]) object);
        }
        else if (clazz == Short.class)
        {
            writeShort((Short) object);
        }
        else if (clazz == Character.class)
        {
            writeChar((Character) object);
        }
        else if (clazz == Integer.class)
        {
            writeInt((Integer) object);
        }
        else if (clazz == Long.class)
        {
            writeLong((Long) object);
        }
        else if (clazz == Float.class)
        {
            writeFloat((Float) object);
        }
        else if (clazz == Double.class)
        {
            writeDouble((Double) object);
        }
        else if (clazz == String.class)
        {
            writeUTF((String) object);
        }
        else
        {
            throw new MessageFormatException("Only primitives plus byte arrays and String are valid types");
        }
    }

    private void checkAvailable(final int i) throws MessageEOFException
    {
        try
        {
            _typedBytesContentReader.checkAvailable(1);
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
    }
}