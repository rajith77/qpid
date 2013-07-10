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

public class TextMessageImpl extends MessageImpl implements TextMessage, ContentTypes
{
    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    private CharsetDecoder _decoder;

    private CharsetEncoder _encoder;

    private JMSException _exception;

    private String _string;

    private ByteBuffer _encodedBytes;

    TextMessageImpl()
    {
        super();
        try
        {
            setBooleanProperty(PAYLOAD_NULL_PROPERTY, true);
            getMessageProperties().setContentType(TEXT_PLAIN);
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
                _string = null;
            }
            else
            {
                _decoder = DEFAULT_CHARSET.newDecoder();
                _string = _decoder.decode(data).toString();
                _encodedBytes = data;
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
        if (_exception != null)
        {
            throw _exception;
        }
        if (_encodedBytes == null)
        {
            if (_string == null)
            {
                _encodedBytes = EMPTY_BYTE_BUFFER;
            }
            else
            {
                try
                {
                    _encoder = DEFAULT_CHARSET.newEncoder();
                    _encoder.reset();
                    _encodedBytes = _encoder.encode(CharBuffer.wrap(_string));
                }
                catch (CharacterCodingException e)
                {
                    throw ExceptionHelper.handleMessageException("Cannot encode string in UFT-8: " + _string, e);
                }
            }
        }
        return _encodedBytes;
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _string = null;
        _encodedBytes = null;
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
        return _string;
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
        _string = txt;
    }
}