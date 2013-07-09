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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import org.apache.qpid.amqp_0_10.jms.impl.MessageImpl;
import org.apache.qpid.amqp_0_10.jms.impl.SessionImpl;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.util.ExceptionHelper;

public class TextMessageImpl extends MessageImpl implements TextMessage
{
    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    private CharsetDecoder _decoder;

    private CharsetEncoder _encoder;

    private JMSException _exception;

    private String _decodedValue;

    TextMessageImpl()
    {
        super();
        try
        {
            setBooleanProperty(PAYLOAD_NULL_PROPERTY, true);
            getMessageProperties().setContentType("text/plain");
            getMessageProperties().setContentEncoding("UTF-8");
        }
        catch (JMSException e)
        {
            // ignore
        }
    }

    TextMessageImpl(SessionImpl ssn, int transferId, DeliveryProperties deliveryProps, MessageProperties msgProps,
            ByteBuffer data)
    {
        super(ssn, transferId, deliveryProps, msgProps);
        try
        {
            if (propertyExists(PAYLOAD_NULL_PROPERTY) || data == null || data.remaining() == 0)
            {
                _decodedValue = null;
            }
            else
            {
                _decoder = DEFAULT_CHARSET.newDecoder();
                _decodedValue = _decoder.decode(data).toString();
            }
        }
        catch (CharacterCodingException e)
        {
            _exception = ExceptionHelper.handleMessageException("Error decoding text message", e);
        }
        catch (JMSException e)
        {
            _exception = e;
        }
    }

    @Override
    public ByteBuffer getContent() throws JMSException
    {
        try
        {
            if (_exception != null)
            {
                throw _exception;
            }
            else if (_decodedValue == null)
            {
                return EMPTY_BYTE_BUFFER;
            }
            else
            {
                _encoder = DEFAULT_CHARSET.newEncoder();
                _encoder.reset();
                return _encoder.encode(CharBuffer.wrap(_decodedValue));
            }
        }
        catch (CharacterCodingException e)
        {
            throw ExceptionHelper.handleMessageException("Cannot encode string in UFT-8: " + _decodedValue, e);
        }
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _decodedValue = null;
        _exception = null;
    }

    @Override
    public String getText() throws JMSException
    {
        isContentReadable();
        if (_exception != null)
        {
            throw _exception;
        }
        return _decodedValue;
    }

    @Override
    public void setText(String txt) throws JMSException
    {
        isContentWritable();
        if (txt == null)
        {
            setBooleanProperty(PAYLOAD_NULL_PROPERTY, true);
        }
        else
        {
            removeProperty(PAYLOAD_NULL_PROPERTY);
        }
        _decodedValue = txt;
    }
}