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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

import javax.jms.JMSException;
import javax.jms.ObjectMessage;

import org.apache.qpid.amqp_0_10.jms.impl.MessageImpl;
import org.apache.qpid.amqp_0_10.jms.impl.SessionImpl;
import org.apache.qpid.client.util.ClassLoadingAwareObjectInputStream;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.util.ByteBufferInputStream;
import org.apache.qpid.util.ExceptionHelper;

public class ObjectMessageImpl extends MessageImpl implements ObjectMessage, ContentTypes
{
    private static final int DEFAULT_OUTPUT_BUFFER_SIZE = 256;

    private Serializable _serializable;

    private ByteBuffer _encodedBytes;

    private JMSException _exception;

    public ObjectMessageImpl()
    {
        super();
        getMessageProperties().setContentType(JAVA_OBJECT);
    }

    public ObjectMessageImpl(SessionImpl ssn, int transferId, DeliveryProperties deliveryProps,
            MessageProperties msgProps, ByteBuffer data)
    {
        super(ssn, transferId, deliveryProps, msgProps);
        try
        {
            _serializable = read(data);
            _encodedBytes = data;
        }
        catch (Exception e)
        {
            _exception = ExceptionHelper.handleMessageException("Error decoding object message", e);
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

            if (_serializable == null)
            {
                _encodedBytes = EMPTY_BYTE_BUFFER;
            }
            else
            {
                try
                {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream(DEFAULT_OUTPUT_BUFFER_SIZE);
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(_serializable);
                    oos.flush();
                    _encodedBytes = ByteBuffer.wrap(baos.toByteArray());
                }
                catch (IOException e)
                {
                    throw ExceptionHelper.handleMessageException("Unable to encode object of type: "
                            + _serializable.getClass().getName() + ", value " + _serializable, e);
                }
            }
        }
        return _encodedBytes;

    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _serializable = null;
        _encodedBytes = null;
        _exception = null;
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
    public Serializable getObject() throws JMSException
    {
        isContentReadable();
        return _serializable;
    }

    @Override
    public void setObject(Serializable serializable) throws JMSException
    {
        isContentWritable();
        clearBody();
        _serializable = serializable;
    }

    private Serializable read(final ByteBuffer data) throws IOException, ClassNotFoundException
    {
        Serializable result = null;
        if (data != null && data.hasRemaining())
        {
            ClassLoadingAwareObjectInputStream in = new ClassLoadingAwareObjectInputStream(new ByteBufferInputStream(
                    data));
            try
            {
                result = (Serializable) in.readObject();
            }
            finally
            {
                in.close();
            }
        }
        return result;
    }
}