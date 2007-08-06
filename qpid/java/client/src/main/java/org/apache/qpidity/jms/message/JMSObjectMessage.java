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
package  org.apache.qpidity.jms.message;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import javax.jms.ObjectMessage;

import org.apache.mina.common.ByteBuffer;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;

public class JMSObjectMessage extends AbstractJMSMessage implements ObjectMessage
{
    public static final String MIME_TYPE = "application/java-object-stream";
    private static final AMQShortString MIME_TYPE_SHORT_STRING = new AMQShortString(MIME_TYPE);

    private static final int DEFAULT_BUFFER_SIZE = 1024;

    /**
     * Creates empty, writable message for use by producers
     */
    public JMSObjectMessage()
    {
        this(null);
    }

    private JMSObjectMessage(ByteBuffer data)
    {
        super(data);
        if (data == null)
        {
            _data = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
            _data.setAutoExpand(true);
        }

        getContentHeaderProperties().setContentType(MIME_TYPE_SHORT_STRING);
    }

    /**
     * Creates read only message for delivery to consumers
     */
    JMSObjectMessage(long messageNbr, ContentHeaderBody contentHeader, AMQShortString exchange, AMQShortString routingKey,
        ByteBuffer data) throws AMQException
    {
        super(messageNbr, (BasicContentHeaderProperties) contentHeader.properties, exchange, routingKey, data);
    }

    public void clearBodyImpl() throws JMSException
    {
        if (_data != null)
        {
            _data.release();
        }

        _data = null;

    }

    public String toBodyString() throws JMSException
    {
        return toString(_data);
    }

    public AMQShortString getMimeTypeAsShortString()
    {
        return MIME_TYPE_SHORT_STRING;
    }

    public void setObject(Serializable serializable) throws JMSException
    {
        checkWritable();

        if (_data == null)
        {
            _data = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
            _data.setAutoExpand(true);
        }
        else
        {
            _data.rewind();
        }

        try
        {
            ObjectOutputStream out = new ObjectOutputStream(_data.asOutputStream());
            out.writeObject(serializable);
            out.flush();
            out.close();
        }
        catch (IOException e)
        {
            MessageFormatException mfe = new MessageFormatException("Message not serializable: " + e);
            mfe.setLinkedException(e);
            throw mfe;
        }

    }

    public Serializable getObject() throws JMSException
    {
        ObjectInputStream in = null;
        if (_data == null)
        {
            return null;
        }

        try
        {
            _data.rewind();
            in = new ObjectInputStream(_data.asInputStream());

            return (Serializable) in.readObject();
        }
        catch (IOException e)
        {
            MessageFormatException mfe = new MessageFormatException("Could not deserialize message: " + e);
            mfe.setLinkedException(e);
            throw mfe;
        }
        catch (ClassNotFoundException e)
        {
            MessageFormatException mfe = new MessageFormatException("Could not deserialize message: " + e);
            mfe.setLinkedException(e);
            throw mfe;
        }
        finally
        {
            _data.rewind();
            close(in);
        }
    }

    private static void close(InputStream in)
    {
        try
        {
            if (in != null)
            {
                in.close();
            }
        }
        catch (IOException ignore)
        { }
    }

    private static String toString(ByteBuffer data)
    {
        if (data == null)
        {
            return null;
        }

        int pos = data.position();
        try
        {
            return data.getString(Charset.forName("UTF8").newDecoder());
        }
        catch (CharacterCodingException e)
        {
            return null;
        }
        finally
        {
            data.position(pos);
        }
    }
}
