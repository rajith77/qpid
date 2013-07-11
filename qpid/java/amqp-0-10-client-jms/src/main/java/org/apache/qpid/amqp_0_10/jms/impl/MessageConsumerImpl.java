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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

import org.apache.qpid.amqp_0_10.jms.impl.AddressResolution.CheckMode;
import org.apache.qpid.transport.MessageCreditUnit;
import org.apache.qpid.transport.MessageFlowMode;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.Range;
import org.apache.qpid.transport.RangeSet;
import org.apache.qpid.transport.RangeSetFactory;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.ConditionManager;
import org.apache.qpid.util.ExceptionHelper;

public class MessageConsumerImpl implements MessageConsumer
{
    private static final Logger _logger = Logger.get(MessageConsumerImpl.class);

    private final SessionImpl _session;

    private final DestinationImpl _dest;

    private final String _selector;

    private final boolean _noLocal;

    private final int _capacity;

    private final String _consumerTag;

    private final AcknowledgeMode _ackMode;

    private final LinkedBlockingQueue<MessageImpl> _localQueue;

    private final List<MessageImpl> _replayQueue;

    private final RangeSet _completions = RangeSetFactory.createRangeSet();

    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private final ConditionManager _msgDeliveryInProgress = new ConditionManager(false);

    private final ConditionManager _msgDeliveryStopped = new ConditionManager(true);

    private final AtomicBoolean _closeFromOnMessage = new AtomicBoolean(false);

    private String _subscriptionQueue;

    private volatile MessageListener _msgListener;

    private Thread _syncReceiveThread;

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
        _capacity = AddressResolution.evaluateCapacity(_session.getConnection().getConfig().getMaxPrefetch(), _dest,
                CheckMode.RECEIVER);
        _localQueue = new LinkedBlockingQueue<MessageImpl>(_capacity);

        switch (ackMode)
        {
        case TRANSACTED:
        case CLIENT_ACK:
        case DUPS_OK:
            // we may want to revisit this for perf reasons.
            _replayQueue = new ArrayList<MessageImpl>(_capacity / 2);
            break;
        default:
            _replayQueue = Collections.<MessageImpl> emptyList();
            break;
        }

        createSubscription();
    }

    void createSubscription() throws JMSException
    {
        try
        {
            _subscriptionQueue = AddressResolution.verifyAndCreateSubscription(_session, _dest, _consumerTag, _ackMode,
                    _noLocal);
            setMessageFlowMode();
            setMessageCredit(_capacity);
            _session.getAMQPSession().sync();
        }
        catch (Exception se)
        {
            throw ExceptionHelper.toJMSException("Error creating consumer.", se);
        }
        _logger.debug("Sucessfully created message consumer for : " + _dest);
    }

    @Override
    public MessageListener getMessageListener() throws JMSException
    {
        checkClosed();
        return _msgListener;
    }

    @Override
    public String getMessageSelector() throws JMSException
    {
        checkClosed();
        return _selector;
    }

    public boolean getNoLocal() throws JMSException
    {
        checkClosed();
        return _noLocal;
    }

    protected DestinationImpl getDestination()
    {
        return _dest;
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException
    {
        checkClosed();
        if (_msgListener != null && _session.getConnection().isStarted())
        {
            throw new IllegalStateException("Message delivery is in progress with another listener");
        }
        _msgListener = listener;
    }

    @Override
    public void close() throws JMSException
    {
        closeImpl(true, true);
    }
    
    /**
     * @param sendClose  : Whether to send protocol close.
     * @param unregister : Whether to unregister from the session.
     */
    void closeImpl(boolean sendClose, boolean unregister) throws JMSException
    {    
        if (Thread.currentThread() == _session.getDispatcherThread() && _msgDeliveryInProgress.getCurrentValue())
        {
            _closeFromOnMessage.set(true);
            return;
        }

        if (!_closed.get())
        {
            _closed.set(true);
            if(unregister)
            {
                _session.removeConsumer(this);
            }

            if (_msgDeliveryStopped.getCurrentValue())
            {
                // wake up anything waiting on _msgDeliveryStopped and return.
            }
            else
            {
                waitForInProgressDeliveriesToStop();
            }
            if (sendClose)
            {
                cancelSubscription();
                releaseMessages();
                AddressResolution.cleanupForConsumer(_session, _dest, _subscriptionQueue);
            }
        }
    }

    @Override
    public MessageImpl receive() throws JMSException
    {
        return receiveImpl(0);
    }

    @Override
    public MessageImpl receive(long timeout) throws JMSException
    {
        return receiveImpl(timeout);
    }

    @Override
    public MessageImpl receiveNoWait() throws JMSException
    {
        return receiveImpl(-1L);
    }

    MessageImpl receiveImpl(long timeout) throws JMSException
    {
        checkClosed();
        if (_msgListener != null)
        {
            throw new IllegalStateException("A listener has already been set.");
        }

        long remaining = timeout;
        if (_msgDeliveryStopped.getCurrentValue())
        {
            remaining =_msgDeliveryStopped.waitUntilFalse(remaining);
            // Time out waiting for message delivery to start.
            if (remaining < 0)
            {
                return null;
            }
        }

        _syncReceiveThread = Thread.currentThread();
        _msgDeliveryInProgress.setValueAndNotify(true);
        MessageImpl m = null;
        try
        {
            if (_localQueue.isEmpty() && isPrefetchDisabled())
            {
                setMessageCredit(1);
                sendMessageFlush();
                sync(remaining);
            }
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
        }
        finally
        {
            _syncReceiveThread = null;
            _msgDeliveryInProgress.setValueAndNotify(false);
        }
        return m;
    }

    void messageReceived(MessageImpl m)
    {
        if (_closed.get())
        {
            try
            {
                releaseMessage(m);
            }
            catch (JMSException e)
            {
                _logger.warn(e, "Error trying to release message for closed consumer");
            }
        }

        try
        {
            _msgDeliveryInProgress.setValueAndNotify(true);
            _msgDeliveryStopped.waitUntilFalse();
            if (_msgListener != null)
            {
                _msgListener.onMessage(m);
                try
                {
                    if (isPrefetchDisabled())
                    {
                        setMessageCredit(1);
                        sendMessageFlush();
                    }
                    postDeliver(m);
                }
                catch (JMSException e)
                {
                    _logger.warn(e, "Error during post onMessage operations");
                }
            }
            else
            {
                try
                {
                    _localQueue.put(m);
                }
                catch (InterruptedException e)
                {
                    // TODO
                }
            }
        }
        finally
        {
            _msgDeliveryInProgress.setValueAndNotify(false);
            if (_closeFromOnMessage.get())
            {
                try
                {
                    close();
                }
                catch (JMSException e)
                {
                    _logger.warn(e, "Error trying to close consumer");
                }
            }
        }
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
        case CLIENT_ACK:
            _replayQueue.add(m);
        case DUPS_OK:
            _replayQueue.add(m);
            if (_replayQueue.size() >= _capacity)
            {
                sendMessageAccept(false);
            }
        default: // NO_ACK
            break;
        }
    }

    void start() throws JMSException
    {
        setMessageCredit(_capacity);
        startMessageDelivery();
    }

    // The session impl will sync once it issues a stop on all it's consumers.
    void stop() throws JMSException
    {
        stopMessageDelivery();
        try
        {
            _session.getAMQPSession().messageStop(_consumerTag, Option.UNRELIABLE);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error sending message.stop.", e);
        }
    }

    /*
     * Will start delivering messages in it's local queue via ML or receive
     * methods.
     */
    void startMessageDelivery()
    {
        _msgDeliveryStopped.setValueAndNotify(false);
    }

    /*
     * When this method returns, this consumer will not deliver any messages (in
     * it's local queue) via it's MessageListener or the receive methods.
     */
    void stopMessageDelivery()
    {        
        waitForInProgressDeliveriesToStop();
        _msgDeliveryStopped.setValueAndNotify(true);
    }

    void requeueUnackedMessages() throws JMSException
    {
        ArrayList<MessageImpl> tmp = new ArrayList<MessageImpl>(_localQueue.size());
        _localQueue.drainTo(tmp);

        for (int i = _replayQueue.size() - 1; i >= 0; i--)
        {
            MessageImpl m = _replayQueue.get(i);
            m.getDeliveryProperties().setRedelivered(true);
            _localQueue.add(m);
        }
        _localQueue.addAll(tmp);
    }

    void releaseMessages() throws JMSException
    {
        try
        {
            RangeSet unacked = RangeSetFactory.createRangeSet();
            for (MessageImpl m : _replayQueue)
            {
                unacked.add(m.getTransferId());
            }
            _session.getAMQPSession().messageRelease(unacked, Option.REDELIVERED);
            _replayQueue.clear();
            RangeSet prefetched = RangeSetFactory.createRangeSet();
            for (MessageImpl m : _localQueue)
            {
                prefetched.add(m.getTransferId());
            }
            _localQueue.clear();
            _session.getAMQPSession().messageRelease(prefetched);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error releasing messages.", e);
        }
    }

    void releaseMessage(MessageImpl m) throws JMSException
    {
        try
        {
            RangeSet range = RangeSetFactory.createRangeSet();
            range.add(m.getTransferId());
            _session.getAMQPSession().messageRelease(range);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error releasing messages.", e);
        }
    }

    // In 0-10 completions affects message credit
    void sendCompleted(MessageImpl m)
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

    void sendMessageAccept(MessageImpl m, boolean sync) throws JMSException
    {
        try
        {
            RangeSet range = RangeSetFactory.createRangeSet();
            range.add(m.getTransferId());
            _session.getAMQPSession().messageAccept(range);
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

    void sendMessageAccept(boolean sync) throws JMSException
    {
        try
        {
            RangeSet range = RangeSetFactory.createRangeSet();
            for (MessageImpl m : _replayQueue)
            {
                range.add(m.getTransferId());
            }
            _session.getAMQPSession().messageAccept(range);
            if (sync)
            {
                _session.getAMQPSession().sync();
            }
            _replayQueue.clear();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Exception when trying to send message accepts", e);
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
            if (isStarted())
            {
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
                startMessageDelivery();
            }
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error setting message credit.", e);
        }
    }

    private void waitForInProgressDeliveriesToStop()
    {
        if (_syncReceiveThread != null)
        {
            try
            {
                // The ref can be null btw the null check and interrupt call
                // as we don't protect it using a lock.
                _syncReceiveThread.interrupt();
            }
            catch (NullPointerException e)
            {
                // ignore.
            }
        }
        _msgDeliveryInProgress.waitUntilFalse();
    }

    private void cancelSubscription() throws JMSException
    {
        try
        {
            _session.getAMQPSession().messageCancel(_consumerTag);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error cancelling subscription", e);
        }
    }

    private void sendMessageFlush() throws JMSException
    {
        try
        {
            _session.getAMQPSession().messageFlush(_consumerTag);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error sending message.flush", e);
        }
    }

    private void sync(long timeout) throws JMSException
    {
        try
        {
            _session.getAMQPSession().sync(timeout);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error waiting for command completion", e);
        }
    }

    private void checkClosed() throws JMSException
    {
        if (_closed.get())
        {
            throw new IllegalStateException("Consumer is closed");
        }
        _session.checkClosed();
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