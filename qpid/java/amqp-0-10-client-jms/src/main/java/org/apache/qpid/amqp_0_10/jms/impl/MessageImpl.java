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
package org.apache.qpid.amqp_0_10.jms.impl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;

import org.apache.qpid.amqp_0_10.jms.AmqpMessage;
import org.apache.qpid.amqp_0_10.jms.impl.dispatch.Dispatchable;
import org.apache.qpid.client.CustomJMSXProperty;
import org.apache.qpid.client.message.QpidMessageProperties;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageDeliveryMode;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.ReplyTo;

public abstract class MessageImpl implements AmqpMessage, Dispatchable<org.apache.qpid.transport.Session>
{
    private static boolean ALLOCATE_DIRECT = Boolean.getBoolean("qpid.allocate-direct");

    /**
     * This constant represents the name of a property that is set when the
     * message payload is null.
     */
    protected static final String PAYLOAD_NULL_PROPERTY = CustomJMSXProperty.JMS_AMQP_NULL.toString();

    protected static final ByteBuffer EMPTY_BYTE_BUFFER = ALLOCATE_DIRECT ? ByteBuffer.allocateDirect(0) : ByteBuffer
            .allocate(0);

    private static final int MAX_CACHED_ENTRIES = Integer.getInteger(ClientProperties.QPID_MAX_CACHED_DEST,
            ClientProperties.DEFAULT_MAX_CACHED_DEST);

    @SuppressWarnings("serial")
    private static final Map<ReplyTo, DestinationImpl> REPLY_TO_DEST_CACHE = Collections
            .synchronizedMap(new LinkedHashMap<ReplyTo, DestinationImpl>(MAX_CACHED_ENTRIES + 1, 1.1f, true)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ReplyTo, DestinationImpl> eldest)
                {
                    return size() > MAX_CACHED_ENTRIES;
                }

            });

    @SuppressWarnings("rawtypes")
    private static final Set<Class> ALLOWED = new HashSet<Class>();
    static
    {
        ALLOWED.add(Boolean.class);
        ALLOWED.add(Byte.class);
        ALLOWED.add(Short.class);
        ALLOWED.add(Integer.class);
        ALLOWED.add(Long.class);
        ALLOWED.add(Float.class);
        ALLOWED.add(Double.class);
        ALLOWED.add(Character.class);
        ALLOWED.add(String.class);
        ALLOWED.add(byte[].class);
    }

    private final boolean isStrictJMS = Boolean.getBoolean("strict-jms");

    private final DeliveryProperties _deliveryProps;

    private final MessageProperties _messageProps;

    private final int _transferId;

    private final String _consumerId;

    private String _messageID = null;

    private boolean _msgReadOnly;

    private SessionImpl _ssn = null;

    private Destination _dest = null;

    private Destination _replyTo = null;

    protected MessageImpl()
    {
        _deliveryProps = new DeliveryProperties();
        _messageProps = new MessageProperties();
        _msgReadOnly = false;
        _transferId = -1;
        _consumerId = null;
    }

    protected MessageImpl(SessionImpl ssn, int transferId, String consumerId, DeliveryProperties deliveryProps,
            MessageProperties msgProps)
    {
        _ssn = ssn;
        _deliveryProps = deliveryProps;
        _messageProps = msgProps;
        _msgReadOnly = true;
        _transferId = transferId;
        _consumerId = consumerId;
    }

    @Override
    public void dispatch()
    {
        if (_ssn == null)
        {
            throw new IllegalStateException("Invalid operation");
        }
        _ssn.messageReceived(this);
    }

    @Override
    public org.apache.qpid.transport.Session getKey()
    {
        if (_ssn == null)
        {
            throw new IllegalStateException("Invalid operation");
        }
        return _ssn.getAMQPSession();
    }

    @Override
    public int getId()
    {
        return _transferId;
    }

    @Override
    public void acknowledge() throws JMSException
    {
        if (_ssn == null)
        {
            throw new javax.jms.IllegalStateException(
                    "Illegal operation. You could call acknowledge() only on a received message");
        }
        else if (_ssn.getAMQPSession().isClosing())
        {
            throw new javax.jms.IllegalStateException(
                    "Stale message. Failover has occurred and this message is invalid");
        }
        else
        {
            _ssn.acknowledgeUpTo(getTransferId(), true);
        }
    }

    @Override
    public void clearBody() throws JMSException
    {
        _msgReadOnly = false;
    }

    @Override
    public String getJMSMessageID() throws JMSException
    {
        if (_messageID == null && _messageProps.getMessageId() != null)
        {
            UUID id = _messageProps.getMessageId();
            _messageID = "ID:" + id;
        }
        return _messageID;
    }

    @Override
    public void setJMSMessageID(String messageId) throws JMSException
    {
        if (messageId == null)
        {
            _messageProps.clearMessageId();
        }
        else
        {
            if (messageId.startsWith("ID:"))
            {
                _messageID = messageId;
            }
            else
            {
                throw new JMSException("MessageId '" + messageId
                        + "' is not of the correct format, it must start with ID:");
            }
        }
    }

    /* Used by the internal implementation */
    void setJMSMessageID(UUID messageId) throws JMSException
    {
        if (messageId == null)
        {
            _messageProps.clearMessageId();
        }
        else
        {
            _messageProps.setMessageId(messageId);
        }
    }

    @Override
    public boolean getJMSRedelivered() throws JMSException
    {
        return _deliveryProps.getRedelivered();
    }

    @Override
    public void setJMSRedelivered(boolean b) throws JMSException
    {
        _deliveryProps.setRedelivered(b);
    }

    @Override
    public long getJMSTimestamp() throws JMSException
    {
        return _deliveryProps.getTimestamp();
    }

    @Override
    public void setJMSTimestamp(long timestamp) throws JMSException
    {
        _deliveryProps.setTimestamp(timestamp);
    }

    @Override
    public byte[] getJMSCorrelationIDAsBytes() throws JMSException
    {
        return _messageProps.getCorrelationId();
    }

    @Override
    public void setJMSCorrelationIDAsBytes(byte[] bytes) throws JMSException
    {
        _messageProps.setCorrelationId(bytes);
    }

    @Override
    public void setJMSCorrelationID(String correlationId) throws JMSException
    {

        setJMSCorrelationIDAsBytes(correlationId == null ? null : correlationId.getBytes());
    }

    @Override
    public String getJMSCorrelationID() throws JMSException
    {

        byte[] correlationIDAsBytes = getJMSCorrelationIDAsBytes();
        return correlationIDAsBytes == null ? null : new String(correlationIDAsBytes);
    }

    @Override
    public Destination getJMSReplyTo() throws JMSException
    {
        if (_replyTo != null)
        {
            return _replyTo;
        }
        else
        {
            ReplyTo replyTo = _messageProps.getReplyTo();

            if ((replyTo == null) || ((replyTo.getExchange() == null) && (replyTo.getRoutingKey() == null)))
            {
                return null;
            }
            else
            {
                DestinationImpl dest = REPLY_TO_DEST_CACHE.get(replyTo);

                if (dest == null)
                {
                    String exchange = replyTo.getExchange();
                    String routingKey = replyTo.getRoutingKey();

                    if (exchange.equals(""))
                    {
                        dest = new QueueImpl(routingKey.concat(";{create:always}"));
                    }
                    else
                    {
                        dest = new TopicImpl(exchange.concat("/").concat(routingKey));
                    }
                    REPLY_TO_DEST_CACHE.put(replyTo, dest);
                }

                return dest;
            }
        }
    }

    Destination getReplyToForSending()
    {
        return _replyTo;
    }

    @Override
    public void setJMSReplyTo(Destination dest) throws JMSException
    {
        _replyTo = dest;
        if (dest == null)
        {
            _messageProps.clearReplyTo();
        }
    }

    @Override
    public Destination getJMSDestination() throws JMSException
    {
        return _dest;
    }

    @Override
    public void setJMSDestination(Destination destination)
    {
        _dest = destination;
    }

    @Override
    public int getJMSDeliveryMode() throws JMSException
    {

        MessageDeliveryMode deliveryMode = _deliveryProps.getDeliveryMode();
        if (deliveryMode != null)
        {
            switch (deliveryMode)
            {
            case PERSISTENT:
                return DeliveryMode.PERSISTENT;
            case NON_PERSISTENT:
                return DeliveryMode.NON_PERSISTENT;
            default:
                throw new JMSException("Unknown Message Delivery Mode: " + _deliveryProps.getDeliveryMode());
            }
        }
        else
        {
            return Message.DEFAULT_DELIVERY_MODE;
        }

    }

    @Override
    public void setJMSDeliveryMode(int deliveryMode) throws JMSException
    {
        switch (deliveryMode)
        {
        case DeliveryMode.PERSISTENT:
            _deliveryProps.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            break;
        case DeliveryMode.NON_PERSISTENT:
            _deliveryProps.setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
            break;
        default:
            throw new JMSException("Unknown JMS Delivery Mode: " + deliveryMode);
        }

    }

    @Override
    public long getJMSExpiration() throws JMSException
    {
        return _deliveryProps.getExpiration();
    }

    @Override
    public void setJMSExpiration(long l) throws JMSException
    {
        _deliveryProps.setExpiration(l);
    }

    @Override
    public int getJMSPriority() throws JMSException
    {
        MessageDeliveryPriority messageDeliveryPriority = _deliveryProps.getPriority();
        return messageDeliveryPriority == null ? Message.DEFAULT_PRIORITY : messageDeliveryPriority.getValue();
    }

    @Override
    public void setJMSPriority(int i) throws JMSException
    {
        _deliveryProps.setPriority(MessageDeliveryPriority.get((short) i));
    }

    @Override
    public String getJMSType() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setJMSType(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean propertyExists(String propertyName) throws JMSException
    {
        return (_messageProps.getApplicationHeaders() != null && _messageProps.getApplicationHeaders().containsKey(
                propertyName));
    }

    @Override
    public boolean getBooleanProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        Object o = getApplicationProperty(propertyName);

        if (o instanceof Boolean)
        {
            return ((Boolean) o).booleanValue();
        }
        else if (o instanceof String)
        {
            return Boolean.valueOf((String) o).booleanValue();
        }
        else if (propertyExists(propertyName))
        {
            throw new MessageFormatException("getBooleanProperty(\"" + propertyName
                    + "\") failed as value is not boolean: " + o);
        }
        else
        {
            return Boolean.valueOf(null);
        }
    }

    @Override
    public byte getByteProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        Object o = getApplicationProperty(propertyName);

        if (o instanceof Byte)
        {
            return ((Byte) o).byteValue();
        }
        else if (o instanceof String)
        {
            return Byte.valueOf((String) o).byteValue();
        }
        else if (propertyExists(propertyName))
        {
            throw new MessageFormatException("getByteProperty(\"" + propertyName
                    + "\") failed as value is not a byte: " + o);
        }
        else
        {
            return Byte.valueOf(null);
        }
    }

    @Override
    public short getShortProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        Object o = getApplicationProperty(propertyName);

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
                return Short.valueOf(getByteProperty(propertyName));
            }
            catch (MessageFormatException e)
            {
                throw new MessageFormatException("getShortProperty(\"" + propertyName
                        + "\") failed as value is not a short: " + o);
            }
        }
    }

    @Override
    public int getIntProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        Object o = getApplicationProperty(propertyName);

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
                return Integer.valueOf(getShortProperty(propertyName));
            }
            catch (MessageFormatException e)
            {
                throw new MessageFormatException("getIntProperty(\"" + propertyName
                        + "\") failed as value is not an int: " + o);
            }

        }
    }

    @Override
    public long getLongProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        Object o = getApplicationProperty(propertyName);

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
                return Long.valueOf(getIntProperty(propertyName));
            }
            catch (MessageFormatException e)
            {
                throw new MessageFormatException("getLongProperty(\"" + propertyName
                        + "\") failed as value is not a long: " + o);
            }

        }
    }

    @Override
    public float getFloatProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        Object o = getApplicationProperty(propertyName);

        if (o instanceof Float)
        {
            return ((Float) o).floatValue();
        }
        else if (o instanceof String)
        {
            return Float.valueOf((String) o).floatValue();
        }
        else if (propertyExists(propertyName))
        {
            throw new MessageFormatException("getFloatProperty(\"" + propertyName
                    + "\") failed as value is not a float: " + o);
        }
        else
        {
            throw new NullPointerException("No such property: " + propertyName);
        }
    }

    @Override
    public double getDoubleProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        Object o = getApplicationProperty(propertyName);

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
                return Double.valueOf(getFloatProperty(propertyName));
            }
            catch (MessageFormatException e)
            {
                throw new MessageFormatException("getDoubleProperty(\"" + propertyName
                        + "\") failed as value is not a double: " + o);
            }

        }
    }

    @Override
    public String getStringProperty(String propertyName) throws JMSException
    {
        if (propertyName.equals(CustomJMSXProperty.JMSXUserID.toString()))
        {
            return new String(_messageProps.getUserId());
        }
        else if (QpidMessageProperties.AMQP_0_10_APP_ID.equals(propertyName) && _messageProps.getAppId() != null)
        {
            return new String(_messageProps.getAppId());
        }
        else if (QpidMessageProperties.AMQP_0_10_ROUTING_KEY.equals(propertyName)
                && _deliveryProps.getRoutingKey() != null)
        {
            return _deliveryProps.getRoutingKey();
        }
        else if (isStrictJMS && QpidMessageProperties.QPID_SUBJECT.equals(propertyName))
        {
            return (String) getApplicationProperty("JMS_" + QpidMessageProperties.QPID_SUBJECT);
        }
        else
        {
            checkPropertyName(propertyName);
            Object o = getApplicationProperty(propertyName);

            if (o instanceof String)
            {
                return (String) o;
            }
            else if (o == null)
            {
                return null;
            }
            else if (o.getClass().isArray())
            {
                throw new MessageFormatException("getString(\"" + propertyName + "\") failed as value of type "
                        + o.getClass() + " is an array.");
            }
            else
            {
                return String.valueOf(o);
            }
        }
    }

    @Override
    public Object getObjectProperty(String propertyName) throws JMSException
    {
        checkPropertyName(propertyName);
        return getApplicationProperty(propertyName);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Enumeration getPropertyNames() throws JMSException
    {
        List<String> props = new ArrayList<String>();
        if (_messageProps.getApplicationHeaders() != null && !_messageProps.getApplicationHeaders().isEmpty())
        {
            Map<String, Object> map = _messageProps.getApplicationHeaders();
            for (String prop : map.keySet())
            {
                Object value = map.get(prop);
                if (value instanceof Boolean || value instanceof Number || value instanceof String)
                {
                    props.add(prop);
                }
            }

            return java.util.Collections.enumeration(props);
        }
        else
        {
            return Collections.enumeration(Collections.emptyList());
        }
    }

    @Override
    public void setBooleanProperty(String propertyName, boolean b) throws JMSException
    {
        setApplicationHeader(propertyName, b);
    }

    @Override
    public void setByteProperty(String propertyName, byte b) throws JMSException
    {
        setApplicationHeader(propertyName, b);
    }

    @Override
    public void setShortProperty(String propertyName, short i) throws JMSException
    {
        setApplicationHeader(propertyName, i);
    }

    @Override
    public void setIntProperty(String propertyName, int i) throws JMSException
    {
        setApplicationHeader(propertyName, i);
    }

    @Override
    public void setLongProperty(String propertyName, long l) throws JMSException
    {
        setApplicationHeader(propertyName, l);
    }

    @Override
    public void setFloatProperty(String propertyName, float f) throws JMSException
    {
        setApplicationHeader(propertyName, f);
    }

    @Override
    public void setDoubleProperty(String propertyName, double v) throws JMSException
    {
        setApplicationHeader(propertyName, v);
    }

    @Override
    public void setStringProperty(String propertyName, String value) throws JMSException
    {
        if (QpidMessageProperties.AMQP_0_10_APP_ID.equals(propertyName))
        {
            checkPropertyName(propertyName);
            checkMessageWritable();
            _messageProps.setAppId(value.getBytes());
        }
        else
        {
            setApplicationHeader(propertyName, value);
        }
    }

    @Override
    public void setObjectProperty(String propertyName, Object object) throws JMSException
    {
        if (object == null)
        {
            throw new MessageFormatException("You cannot set a property value to null");
        }
        else if (!ALLOWED.contains(object.getClass()))
        {
            throw new MessageFormatException(object.getClass()
                    + " is not an allowed property type. Types allowed are : " + ALLOWED);
        }
        setApplicationHeader(propertyName, object);
    }

    @Override
    public void clearProperties() throws JMSException
    {
        if (_messageProps.getApplicationHeaders() != null)
        {
            _messageProps.clearApplicationHeaders();
        }
    }

    @Override
    public String toString()
    {
        try
        {
            StringBuffer buf = new StringBuffer();
            headerToString(buf);
            bodyToString(buf);

            return buf.toString();
        }
        catch (JMSException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected abstract void bodyToString(StringBuffer buf) throws JMSException;

    void setContentType(String contentType)
    {
        _messageProps.setContentType(contentType);
    }

    String getContentType()
    {
        return _messageProps.getContentType();
    }

    void setEncoding(String encoding)
    {
        if (encoding == null || encoding.length() == 0)
        {
            _messageProps.clearContentEncoding();
        }
        else
        {
            _messageProps.setContentEncoding(encoding);
        }
    }

    String getEncoding()
    {
        return _messageProps.getContentEncoding();
    }

    Object getApplicationProperty(String name)
    {
        if (_messageProps.getApplicationHeaders() != null && _messageProps.getApplicationHeaders().containsKey(name))
        {
            return _messageProps.getApplicationHeaders().get(name);
        }
        else
        {
            return null;
        }
    }

    void setApplicationHeader(String propertyName, Object object) throws JMSException
    {
        checkMessageWritable();
        checkPropertyName(propertyName);

        Map<String, Object> headers = _messageProps.getApplicationHeaders();
        if (headers == null)
        {
            headers = new HashMap<String, Object>();
            _messageProps.setApplicationHeaders(headers);
        }
        headers.put(propertyName, object);
    }

    protected void removeProperty(String propertyName) throws JMSException
    {
        Map<String, Object> headers = _messageProps.getApplicationHeaders();
        if (headers != null)
        {
            headers.remove(propertyName);
        }
    }

    protected void checkMessageWritable() throws MessageNotWriteableException
    {
        if (_msgReadOnly)
        {
            throw new MessageNotWriteableException(
                    "You need to call clearProperties() to make the message properties writable");
        }
    }

    protected void checkMessageReadable() throws MessageNotReadableException
    {
        if (!_msgReadOnly)
        {
            throw new MessageNotReadableException("You need to call reset() to make the message readable");
        }
    }

    protected void markReadOnly()
    {
        _msgReadOnly = true;
    }

    protected boolean isReadOnly()
    {
        return _msgReadOnly;
    }

    protected void checkPropertyName(CharSequence propertyName) throws JMSException
    {
        if (propertyName == null)
        {
            throw new JMSException("Property name must not be null");
        }
        else if (propertyName.length() == 0)
        {
            throw new JMSException("Property name must not be the empty string");
        }

        checkIdentiferFormat(propertyName);
    }

    // Set by the provider (after resolving the address) before sending
    void setAMQPDestination(String exchange, String routingKey)
    {
        _deliveryProps.setExchange(exchange);
        _deliveryProps.setRoutingKey(routingKey);
    }

    // Set by the provider (after resolving the address) before sending
    void setAMQPReplyTo(ReplyTo replyTo) throws JMSException
    {
        _messageProps.setReplyTo(replyTo);
    }

    void checkIdentiferFormat(CharSequence propertyName)
    {
        // JMS requirements 3.5.1 Property Names
        // Identifiers:
        // - An identifier is an unlimited-length character sequence that must
        // begin
        // with a Java identifier start character; all following characters must
        // be Java
        // identifier part characters. An identifier start character is any
        // character for
        // which the method Character.isJavaIdentifierStart returns true. This
        // includes
        // '_' and '$'. An identifier part character is any character for which
        // the
        // method Character.isJavaIdentifierPart returns true.
        // - Identifiers cannot be the names NULL, TRUE, or FALSE.
        // Identifiers cannot be NOT, AND, OR, BETWEEN, LIKE, IN, IS, or
        // ESCAPE.
        // Identifiers are either header field references or property
        // references. The
        // type of a property value in a message selector corresponds to the
        // type
        // used to set the property. If a property that does not exist in a
        // message is
        // referenced, its value is NULL. The semantics of evaluating NULL
        // values
        // in a selector are described in Section 3.8.1.2, Null Values.
        // The conversions that apply to the get methods for properties do not
        // apply when a property is used in a message selector expression. For
        // example, suppose you set a property as a string value, as in the
        // following:
        // myMessage.setStringProperty("NumberOfOrders", "2")
        // The following expression in a message selector would evaluate to
        // false,
        // because a string cannot be used in an arithmetic expression:
        // "NumberOfOrders > 1"
        // Identifiers are case sensitive.
        // Message header field references are restricted to JMSDeliveryMode,
        // JMSPriority, JMSMessageID, JMSTimestamp, JMSCorrelationID, and
        // JMSType. JMSMessageID, JMSCorrelationID, and JMSType values may be
        // null and if so are treated as a NULL value.

        if (isStrictJMS)
        {
            // JMS start character
            if (!(Character.isJavaIdentifierStart(propertyName.charAt(0))))
            {
                throw new IllegalArgumentException("Identifier '" + propertyName
                        + "' does not start with a valid JMS identifier start character");
            }

            // JMS part character
            int length = propertyName.length();
            for (int c = 1; c < length; c++)
            {
                if (!(Character.isJavaIdentifierPart(propertyName.charAt(c))))
                {
                    throw new IllegalArgumentException("Identifier '" + propertyName
                            + "' contains an invalid JMS identifier character");
                }
            }

            // JMS invalid names
            if ((propertyName.equals("NULL") || propertyName.equals("TRUE") || propertyName.equals("FALSE")
                    || propertyName.equals("NOT") || propertyName.equals("AND") || propertyName.equals("OR")
                    || propertyName.equals("BETWEEN") || propertyName.equals("LIKE") || propertyName.equals("IN")
                    || propertyName.equals("IS") || propertyName.equals("ESCAPE")))
            {
                throw new IllegalArgumentException("Identifier '" + propertyName + "' is not allowed in JMS");
            }
        }
    }

    void headerToString(StringBuffer buf) throws JMSException
    {
        buf.append("\nJMS MessageID: ").append(getJMSMessageID());
        buf.append("\nJMS Destination: ").append(getJMSDestination());
        buf.append("\nJMS Type: ").append(getJMSType());
        buf.append("\nJMS Content-Type: ").append(getContentType());
        buf.append("\nAMQ message number: ").append(_transferId);
        buf.append("\nJMS Correlation ID: ").append(getJMSCorrelationID());
        buf.append("\nJMS timestamp: ").append(getJMSTimestamp());
        buf.append("\nJMS expiration: ").append(getJMSExpiration());
        buf.append("\nJMS priority: ").append(getJMSPriority());
        buf.append("\nJMS delivery mode: ").append(
                getJMSDeliveryMode() == DeliveryMode.PERSISTENT ? "PERSISTENT" : "NON_PERSISTENT");
        buf.append("\nJMS reply to: ").append(getJMSReplyTo());
        buf.append("\nJMS Redelivered: ").append(getJMSRedelivered());

        buf.append("\nProperties:");
        @SuppressWarnings("rawtypes")
        final Enumeration propertyNames = getPropertyNames();
        if (!propertyNames.hasMoreElements())
        {
            buf.append("<NONE>");
        }
        else
        {
            buf.append("\n{");
            while (propertyNames.hasMoreElements())
            {
                String propertyName = (String) propertyNames.nextElement();
                buf.append("\t").append(propertyName).append(" = ").append(getObjectProperty(propertyName))
                        .append("\n");
            }
            buf.append("}");
        }
    }

    @Override
    public DeliveryProperties getDeliveryProperties()
    {
        return _deliveryProps;
    }

    @Override
    public MessageProperties getMessageProperties()
    {
        return _messageProps;
    }

    @Override
    public int getTransferId()
    {
        return _transferId;
    }

    @Override
    public String getConsumerId()
    {
        return _consumerId;
    }
}