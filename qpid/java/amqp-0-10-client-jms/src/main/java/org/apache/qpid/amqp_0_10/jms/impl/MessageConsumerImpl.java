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

import static org.apache.qpid.transport.Option.BATCH;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

import org.apache.qpid.transport.MessageCreditUnit;
import org.apache.qpid.transport.MessageFlowMode;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.Range;
import org.apache.qpid.transport.RangeSet;
import org.apache.qpid.transport.RangeSetFactory;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.ExceptionHelper;

public class MessageConsumerImpl implements MessageConsumer
{
    private static final Logger _logger = Logger.get(MessageConsumerImpl.class);

    private final SessionImpl _session;

    private final DestinationImpl _dest;

    private final String _selector;

    private final boolean _noLocal;

    private final String _subscriptionQueue;

    private final int _capacity;

    private final String _consumerTag;

    private final AcknowledgeMode _ackMode;

    private final LinkedBlockingQueue<MessageImpl> _localQueue;

    private final RangeSet _completions = RangeSetFactory.createRangeSet();

    private final RangeSet _unacked = RangeSetFactory.createRangeSet();

    private final AtomicBoolean _started = new AtomicBoolean(false);

    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private volatile MessageListener _msgListener;

    private int _unsentCompletions = 0;

    protected MessageConsumerImpl(String consumerTag, SessionImpl ssn, Destination dest, String selector,
            boolean noLocal, boolean browseOnly, AcknowledgeMode ackMode) throws JMSException
    {
        if (!(dest instanceof DestinationImpl))
        {
            throw new InvalidDestinationException("Invalid destination class " + dest.getClass().getName());
        }
        _session = ssn;
        _dest = (DestinationImpl) dest;
        _ackMode = ackMode;
        _selector = selector;
        _noLocal = noLocal;
        _consumerTag = consumerTag;
        _capacity = AddressResolution.evaluateCapacity(0, _dest);
        // TODO get mx prefetch from connnection
        _localQueue = new LinkedBlockingQueue<MessageImpl>(_capacity);

        try
        {
            _subscriptionQueue = AddressResolution.verifyAndCreateSubscription(ssn, _dest, consumerTag, ackMode,
                    noLocal);
            setMessageFlowMode();
            setMessageCredit(_capacity);
            _session.getAMQPSession().sync();
        }
        catch (Exception se)
        {
            throw ExceptionHelper.toJMSException("Error creating consumer.", se);
        }
    }

    private void setMessageFlowMode() throws JMSException
    {
        try
        {
            if (_capacity == 0)
            {
                // No prefetch case
                _session.getAMQPSession().messageSetFlowMode(_consumerTag, MessageFlowMode.CREDIT);
            }
            else
            {
                _session.getAMQPSession().messageSetFlowMode(_consumerTag, MessageFlowMode.WINDOW);
            }
            _session.getAMQPSession().messageFlow(_consumerTag, MessageCreditUnit.BYTE, 0xFFFFFFFF, Option.UNRELIABLE);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error setting message flow mode.", e);
        }
    }

    private void setMessageCredit(int credit) throws JMSException
    {
        try
        {
            if (_session.getConnection().isStarted())
            {
                _started.set(true);
                if (_capacity > 0)
                {
                    _session.getAMQPSession().messageFlow(_consumerTag, MessageCreditUnit.MESSAGE, credit,
                            Option.UNRELIABLE);
                }
                else if (isMessageListener())
                {
                    _session.getAMQPSession()
                            .messageFlow(_consumerTag, MessageCreditUnit.MESSAGE, 1, Option.UNRELIABLE);
                }
            }
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error setting message credit.", e);
        }
    }

    @Override
    public Message receive() throws JMSException
    {
        return receiveImpl(0);
    }

    @Override
    public Message receive(long timeout) throws JMSException
    {
        return receiveImpl(timeout);
    }

    @Override
    public Message receiveNoWait() throws JMSException
    {
        return receiveImpl(-1L);
    }

    Message receiveImpl(long timeout) throws JMSException
    {
        checkPreConditions();
        long remaining = preSyncReceive(timeout);
        if (isStarted() && _localQueue.isEmpty() && isPrefetchDisabled())
        {
            setMessageCredit(1);
        }
        MessageImpl m = null;
        try
        {
            if (timeout > 0)
            {
                m = _localQueue.poll(remaining, TimeUnit.MILLISECONDS);
            }
            else if (timeout < 0)
            {
                m = _localQueue.poll();
            }
            else
            {
                m = _localQueue.take();
            }
        }
        catch (InterruptedException e)
        {
            _logger.warn(e, "Interrupted while waiting for message on local queue.");
        }

        if (m != null)
        {
            postDeliver(m);
        }

        return m;
    }

    void postDeliver(MessageImpl m) throws JMSException
    {
        sendCompleted(m);
        switch (_ackMode)
        {
        case AUTO_ACK:
            sendMessageAccept(m, true);
            break;
        case TRANSACTED:
        case DUPS_OK:
        case CLIENT_ACK:
            _unacked.add(m.getTransferId());
        default: // NO_ACK
            break;
        }
    }

    // In 0-10 completions affects message credit
    private void sendCompleted(MessageImpl m)
    {
        _unsentCompletions++;
        _completions.add(m.getTransferId());
        if (_capacity == 0 || _unsentCompletions > _capacity / 2)
        {
            for (final Range range : _completions)
            {
                _session.getAMQPSession().processed(range);
            }
            _session.getAMQPSession().flushProcessed(BATCH);
            _completions.clear();
            _unsentCompletions = 0;
        }
    }

    private void sendMessageAccept(MessageImpl m, boolean sync) throws JMSException
    {
        _unacked.add(m.getTransferId());
        try
        {
            _session.getAMQPSession().messageAccept(_unacked);
            _unacked.clear();
            if (sync)
            {
                _session.getAMQPSession().sync();
            }
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Exception when trying to send message accepts", e);
        }
    }

    private void sendMessageAccept(boolean sync) throws JMSException
    {
        try
        {
            _session.getAMQPSession().messageAccept(_unacked);
            if (sync)
            {
                _session.getAMQPSession().sync();
            }
            _unacked.clear();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Exception when trying to send message accepts", e);
        }
    }

    void commit() throws JMSException
    {
        sendMessageAccept(false);
    }

    void rollback()
    {
        _session.getAMQPSession().messageRelease(_unacked, Option.REDELIVERED);
        _unacked.clear();
    }

    void startMessageDelivery() throws JMSException
    {
        _started.set(true);
        setMessageCredit(_capacity);
    }

    /*
     * When this method returns, this consumer will not deliver any messages (in
     * it's local queue) via it's MessageListener or the receive methods. The
     * session impl will sync once it issues a stop on all it's consumers.
     */
    void stopMessageDelivery() throws JMSException
    {
        _started.set(false);
        // wait for any in-progress deliveries to stop.
        _session.getAMQPSession().messageStop(_consumerTag, Option.UNRELIABLE);
    }

    void releaseMessages() throws JMSException
    {
        _session.getAMQPSession().messageRelease(_unacked, Option.REDELIVERED);
        _unacked.clear();
        RangeSet prefetched = RangeSetFactory.createRangeSet();
        for (MessageImpl m : _localQueue)
        {
            prefetched.add(m.getTransferId());
        }
        _localQueue.clear();
        _session.getAMQPSession().messageRelease(prefetched);
    }

    void closed()
    {
        if (!_closed.get())
        {
            _closed.set(true);
        }
    }

    @Override
    public void close() throws JMSException
    {
        if (!_closed.get())
        {
            _closed.set(true);
            cancelSubscription();
            AddressResolution.cleanupForConsumer(_session, _dest, _subscriptionQueue);
        }
    }

    private void cancelSubscription()
    {
        _session.getAMQPSession().messageCancel(_consumerTag);
    }

    @Override
    public MessageListener getMessageListener() throws JMSException
    {
        return _msgListener;
    }

    @Override
    public String getMessageSelector() throws JMSException
    {
        return _selector;
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException
    {
        checkPreConditions();
        if (_msgListener != null && _started.get())
        {
            throw new IllegalStateException("Message delivery is in progress with another listener");
        }
        _msgListener = listener;
    }

    private void checkPreConditions() throws JMSException
    {   
        if (_closed.get())
        {
            throw new IllegalStateException("Consumer is closed");
        }
        _session.checkClosed();
    }

    private long preSyncReceive(long timeout) throws JMSException
    {
        if (_msgListener != null)
        {
            throw new IllegalStateException("A listener has already been set.");
        }

        long remaining = timeout;
        while (_session.getConnection().isFailoverInProgress() && remaining > 0)
        {
            try
            {
                remaining = _session.getConnection().waitForFailoverToComplete(remaining);
            }
            catch (InterruptedException e)
            {
                _logger.warn(e, "Consumer sync receive interrupted while waiting for failover to complete");
            }
        }
        if (_session.getConnection().isFailoverInProgress() && remaining <= 0)
        {
            throw new JMSException("Timeout waiting for connection to failover");
        }

        while (!_session.getConnection().isStarted() && remaining > 0)
        {
            try
            {
                remaining = _session.getConnection().waitForConnectionToStart(remaining);
            }
            catch (InterruptedException e)
            {
                _logger.warn(e, "Consumer sync receive interrupted while waiting for connection to start");
            }
        }
        return remaining;
    }

    private boolean isMessageListener()
    {
        return _msgListener != null;
    }

    private boolean isPrefetchDisabled()
    {
        return _capacity == 0;
    }

    private boolean isStarted()
    {
        return _session.getConnection().isStarted();
    }
}
