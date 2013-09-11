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

import java.util.Enumeration;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageEOFException;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.apache.qpid.amqp_0_10.jms.impl.MessageImpl;

public class MessageConverter
{
    public static MessageImpl convert(Message message) throws JMSException
    {
        if (message instanceof BytesMessage)
        {
            return convertToBytesMsg((BytesMessage) message);
        }
        else if (message instanceof MapMessage)
        {
            return convertToMapMsg((MapMessage) message);
        }
        else if (message instanceof ObjectMessage)
        {
            return convertToObjectMsg((ObjectMessage) message);
        }
        else if (message instanceof TextMessage)
        {
            return convertToTextMsg((TextMessage) message);
        }
        else if (message instanceof StreamMessage)
        {
            return convertToStreamMsg((StreamMessage) message);
        }
        else
        {
            return convertToGenericMsg(message);
        }
    }

    static BytesMessageImpl convertToBytesMsg(BytesMessage msg) throws JMSException
    {
        msg.reset();
        BytesMessageImpl nativeMsg = new BytesMessageImpl();

        byte[] buf = new byte[1024];
        int len;

        while ((len = msg.readBytes(buf)) != -1)
        {
            nativeMsg.writeBytes(buf, 0, len);
        }

        setMessageProperties(msg, nativeMsg);
        return nativeMsg;
    }

    static MapMessageImpl convertToMapMsg(MapMessage msg) throws JMSException
    {
        MapMessageImpl nativeMsg = new MapMessageImpl();

        Enumeration mapNames = msg.getMapNames();
        while (mapNames.hasMoreElements())
        {
            String name = (String) mapNames.nextElement();
            nativeMsg.setObject(name, msg.getObject(name));
        }

        setMessageProperties(msg, nativeMsg);
        return nativeMsg;
    }

    static ObjectMessageImpl convertToObjectMsg(ObjectMessage msg) throws JMSException
    {
        ObjectMessageImpl nativeMsg = new ObjectMessageImpl();

        nativeMsg.setObject(msg.getObject());

        setMessageProperties(msg, nativeMsg);
        return nativeMsg;
    }

    static TextMessageImpl convertToTextMsg(TextMessage msg) throws JMSException
    {
        TextMessageImpl nativeMsg = new TextMessageImpl();

        nativeMsg.setText(msg.getText());

        setMessageProperties(msg, nativeMsg);
        return nativeMsg;
    }

    static StreamMessageImpl convertToStreamMsg(StreamMessage msg) throws JMSException
    {
        StreamMessageImpl nativeMsg = new StreamMessageImpl();

        try
        {
            msg.reset();
            while (true)
            {
                nativeMsg.writeObject(msg.readObject());
            }
        }
        catch (MessageEOFException e)
        {
            // we're at the end so don't mind the exception
        }

        setMessageProperties(msg, nativeMsg);
        return nativeMsg;
    }

    static MessageImpl convertToGenericMsg(Message msg) throws JMSException
    {
        MessageImpl nativeMsg = new BytesMessageImpl();

        setMessageProperties(msg, nativeMsg);
        return nativeMsg;
    }

    static void setMessageProperties(Message message, MessageImpl nativeMsg) throws JMSException
    {
        setNonJMSProperties(message, nativeMsg);
        setJMSProperties(message, nativeMsg);
    }

    /**
     * Sets all non-JMS defined properties on converted message
     */
    static void setNonJMSProperties(Message message, MessageImpl nativeMsg) throws JMSException
    {
        Enumeration propertyNames = message.getPropertyNames();
        while (propertyNames.hasMoreElements())
        {
            String propertyName = String.valueOf(propertyNames.nextElement());
            Object value = message.getObjectProperty(propertyName);
            nativeMsg.setObjectProperty(propertyName, value);
        }
    }

    /**
     * Exposed JMS defined properties on converted message:
     * JMSDestination   - we don't set here
     * JMSDeliveryMode  - set
     * JMSExpiration    - we don't set here
     * JMSPriority      - we don't set here
     * JMSMessageID     - we don't set here
     * JMSTimestamp     - we don't set here
     * JMSCorrelationID - set
     * JMSReplyTo       - set
     * JMSType          - set
     * JMSRedlivered    - we don't set here
     */
    static void setJMSProperties(Message message, MessageImpl nativeMsg) throws JMSException
    {
        // TODO There will be issues when converting the destinations.
        /*
         * if (message.getJMSReplyTo() != null) {
         * nativeMsg.setJMSReplyTo(message.getJMSReplyTo()); }
         */

        nativeMsg.setJMSType(message.getJMSType());
        nativeMsg.setJMSCorrelationID(message.getJMSCorrelationID());
    }
}