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
package org.apache.qpid.amqp_0_10.jms.impl;

import static org.apache.qpid.transport.Option.NONE;
import static org.apache.qpid.transport.Option.SYNC;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import org.apache.qpid.amqp_0_10.jms.impl.AddressResolution.CheckMode;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageDeliveryMode;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.ReplyTo;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.ConditionManager;
import org.apache.qpid.util.ExceptionHelper;
import org.apache.qpid.util.Strings;
import org.apache.qpid.util.UUIDGen;
import org.apache.qpid.util.UUIDs;

public class MessageProducerImpl implements MessageProducer
{
    private static final Logger _logger = Logger.get(MessageProducerImpl.class);

    private static final int MAX_CACHED_ENTRIES = Integer.getInteger(ClientProperties.QPID_MAX_CACHED_DEST,
            ClientProperties.DEFAULT_MAX_CACHED_DEST);

    @SuppressWarnings("serial")
    private static final Map<DestinationImpl, ReplyTo> DEST_TO_REPLY_CACHE = Collections
            .synchronizedMap(new LinkedHashMap<DestinationImpl, ReplyTo>(MAX_CACHED_ENTRIES + 1, 1.1f, true)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<DestinationImpl, ReplyTo> eldest)
                {
                    return size() > MAX_CACHED_ENTRIES;
                }

            });

    private final UUIDGen _messageIdGenerator = UUIDs.newGenerator();

    private final SessionImpl _session;

    private final DestinationImpl _dest;

    private final AMQPDestination _amqpDest;

    private final PublishMode _publishMode;

    private final byte[] _userIDBytes;

    private final int _capacity;

    private final boolean _isReplayRequired;

    private final Map<Integer,MessageTransfer> _replayQueue;

    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private final ConditionManager _msgSendingInProgress = new ConditionManager(false);

    private boolean _syncPublish = false;

    private MessageDeliveryMode _deliveryMode = MessageDeliveryMode.get((short) Message.DEFAULT_DELIVERY_MODE);

    private MessageDeliveryPriority _priority = MessageDeliveryPriority.get((short) Message.DEFAULT_PRIORITY);

    private boolean _disableMessageId = false;

    private boolean _disableTimestamp = false;

    private long _ttl = 0;

    protected MessageProducerImpl(SessionImpl ssn, Destination dest) throws JMSException
    {
        if (!(dest instanceof DestinationImpl))
        {
            throw new InvalidDestinationException("Invalid destination class " + dest.getClass().getName());
        }
        _session = ssn;

        _dest = (DestinationImpl) dest;

        _publishMode = ssn.getConnection().getConfig().getPublishMode();

        _userIDBytes = Strings.toUTF8(ssn.getConnection().getAMQPConnection().getUserID());

        _amqpDest = AddressResolution.verifyForProducer(ssn, _dest);

        _syncPublish = getSyncPublish();

        int defaultCapacity = Integer.getInteger(ClientProperties.QPID_SENDER_CAPACITY,
                ClientProperties.DEFAULT_SENDER_CAPACITY);
        _capacity = AddressResolution.evaluateCapacity(defaultCapacity, _dest, CheckMode.SENDER);

        _isReplayRequired = AddressResolution.isReplayRequired(_dest);
        if (!_syncPublish && _isReplayRequired)
        {
            _replayQueue = new HashMap<Integer,MessageTransfer>(_capacity);
        }
        else
        {
            _replayQueue = Collections.emptyMap();
        }

        _logger.debug("Sucessfully created message producer for : " + dest);
    }

    @Override
    public void close() throws JMSException
    {
        if (!_closed.get())
        {
            _closed.set(true);
            _session.removeProducer(this);
            AddressResolution.cleanupForProducer(_session, _dest);
        }
    }

    public void closed()
    {
        if (!_closed.get())
        {
            _closed.set(true);
        }
    }

    @Override
    public void send(Message msg) throws JMSException
    {
        sendImpl(_amqpDest, msg, _deliveryMode, _priority, _ttl, _syncPublish);
    }

    @Override
    public void send(Destination dest, Message msg) throws JMSException
    {
        send(dest, msg, _deliveryMode.getValue(), _priority.getValue(), _ttl);
    }

    @Override
    public void send(Message msg, int deliveryMode, int priority, long timeToLive) throws JMSException
    {
        sendImpl(_amqpDest, msg, MessageDeliveryMode.get((short) deliveryMode),
                MessageDeliveryPriority.get((short) priority), timeToLive, _syncPublish);
    }

    @Override
    public void send(Destination dest, Message msg, int deliveryMode, int priority, long timeToLive)
            throws JMSException
    {
        if (!(dest instanceof DestinationImpl))
        {
            throw new InvalidDestinationException("Invalid destination class " + dest.getClass().getName());
        }
        AMQPDestination amqpDest = AddressResolution.verifyForProducer(_session, (DestinationImpl) dest);
        sendImpl(amqpDest, msg, MessageDeliveryMode.get((short) deliveryMode),
                MessageDeliveryPriority.get((short) priority), timeToLive, _syncPublish);
    }

    void sendImpl(AMQPDestination dest, Message msg, MessageDeliveryMode deliveryMode,
            MessageDeliveryPriority priority, long timeToLive, boolean sync) throws JMSException
    {
        checkClosed();
        MessageImpl message;
        boolean isForeignMsg = false;
        if (msg instanceof MessageImpl)
        {
            message = (MessageImpl) msg;
        }
        else
        {
            message = convertToNativeMessage(msg);
        }

        DeliveryProperties deliveryProps = message.getDeliveryProperties();
        MessageProperties messageProps = message.getMessageProperties();

        // On the receiving side, this will be read in to the JMSXUserID as
        // well.
        messageProps.setUserId(_userIDBytes);

        messageProps.clearMessageId();
        if (!_disableMessageId)
        {
            messageProps.setMessageId(_messageIdGenerator.generate());
        }

        if (timeToLive > 0 || !_disableTimestamp)
        {
            long currentTime = System.currentTimeMillis();
            if (timeToLive > 0)
            {
                deliveryProps.setTtl(timeToLive);
                deliveryProps.setExpiration(currentTime + timeToLive);
            }
            if (!_disableTimestamp)
            {
                deliveryProps.setTimestamp(currentTime);
            }
        }
        if (message.getReplyToForSending() != null)
        {
            setReplyTo(message);
        }

        deliveryProps.setDeliveryMode(deliveryMode);
        deliveryProps.setPriority(priority);

        String exchange = dest.getExchange();
        deliveryProps.setExchange(exchange);
        deliveryProps.setRoutingKey(dest.getRoutingKey());

        ByteBuffer data = message.getContent();
        messageProps.setContentLength(data.remaining());

        try
        {
            if (_session.getConnection().isFailoverInProgress())
            {
                _session.getConnection().waitForFailoverToComplete(0);
            }

            _msgSendingInProgress.setValueAndNotify(true);

            ByteBuffer buffer = data == null ? ByteBuffer.allocate(0) : data.slice();

            MessageTransfer transfer = new MessageTransfer(exchange, MessageAcceptMode.NONE,
                    MessageAcquireMode.PRE_ACQUIRED, new Header(deliveryProps, messageProps), buffer, sync ? SYNC
                            : NONE);

            _session.getAMQPSession().invoke(transfer);
            if (sync)
            {
                _session.getAMQPSession().sync();
            }
            else if (_isReplayRequired)
            {
                _replayQueue.put(transfer.getId(), transfer);
            }
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error sending message", e);
        }
        finally
        {
            _msgSendingInProgress.setValueAndNotify(false);
        }

        if (isForeignMsg)
        {
            updateFiledsInForeignMsg(msg, message);
        }
    }

    @Override
    public int getDeliveryMode() throws JMSException
    {
        checkClosed();
        return _deliveryMode.getValue();
    }

    @Override
    public Destination getDestination() throws JMSException
    {
        checkClosed();
        return _dest;
    }

    @Override
    public boolean getDisableMessageID() throws JMSException
    {
        checkClosed();
        return _disableMessageId;
    }

    @Override
    public boolean getDisableMessageTimestamp() throws JMSException
    {
        checkClosed();
        return _disableTimestamp;
    }

    @Override
    public int getPriority() throws JMSException
    {
        checkClosed();
        return _priority.getValue();
    }

    @Override
    public long getTimeToLive() throws JMSException
    {
        checkClosed();
        return _ttl;
    }

    @Override
    public void setDeliveryMode(int deliveryMode) throws JMSException
    {
        checkClosed();
        _deliveryMode = MessageDeliveryMode.get((short) deliveryMode);
        _syncPublish = getSyncPublish();
    }

    @Override
    public void setDisableMessageID(boolean b) throws JMSException
    {
        checkClosed();
        _disableMessageId = b;
    }

    @Override
    public void setDisableMessageTimestamp(boolean b) throws JMSException
    {
        checkClosed();
        _disableTimestamp = b;
    }

    @Override
    public void setPriority(int priority) throws JMSException
    {
        checkClosed();
        _priority = MessageDeliveryPriority.get((short) priority);
    }

    @Override
    public void setTimeToLive(long ttl) throws JMSException
    {
        checkClosed();
        _ttl = ttl;
    }

    void setReplyTo(MessageImpl m) throws JMSException
    {
        if (!(m.getJMSReplyTo() instanceof DestinationImpl))
        {
            throw new JMSException("ReplyTo destination should be of type " + DestinationImpl.class
                    + " - given argument is of type " + m.getJMSReplyTo().getClass());
        }

        DestinationImpl d = (DestinationImpl) m.getJMSReplyTo();

        ReplyTo replyTo = DEST_TO_REPLY_CACHE.get(d);
        if (replyTo == null)
        {
            replyTo = AddressResolution.getReplyTo(_session, d);
            DEST_TO_REPLY_CACHE.put(d, replyTo);
        }
        m.getMessageProperties().setReplyTo(replyTo);
    }

    boolean getSyncPublish()
    {
        return (_publishMode == PublishMode.SYNC_PUBLISH_ALL)
                || (_publishMode == PublishMode.SYNC_PUBLISH_PERSISTENT && _deliveryMode == MessageDeliveryMode.PERSISTENT);
    }

    MessageImpl convertToNativeMessage(Message msg)
    {
        return null;
    }

    void updateFiledsInForeignMsg(Message msg, MessageImpl message)
    {
        // TODO Auto-generated method stub
    }

    private void checkClosed() throws JMSException
    {
        if (_closed.get())
        {
            throw new IllegalStateException("Producer is closed");
        }
        _session.checkClosed();
    }
}