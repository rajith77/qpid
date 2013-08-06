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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.IllegalStateException;
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
import org.apache.qpid.util.ConditionManagerTimeoutException;
import org.apache.qpid.util.ExceptionHelper;

public class MessageConsumerImpl implements MessageConsumer
{
    private static final Logger _logger = Logger.get(MessageConsumerImpl.class);

    private final SessionImpl _session;

    private final DestinationImpl _dest;

    private final String _selector;

    private final boolean _noLocal;

    private final int _capacity;

    private final String _consumerId;

    private final AcknowledgeMode _ackMode;

    private final LinkedBlockingQueue<MessageImpl> _localQueue;

    private final List<MessageImpl> _replayQueue;

    private final RangeSet _completions = RangeSetFactory.createRangeSet();

    private final AtomicBoolean _closing = new AtomicBoolean(false);

    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private final ConditionManager _msgDeliveryInProgress = new ConditionManager(false);

    private final ConditionManager _msgDeliveryStopped = new ConditionManager(true);

    private final AtomicBoolean _redeliverCurrentMsg = new AtomicBoolean(false);

    private String _subscriptionQueue;

    private volatile MessageListener _msgListener;

    private Thread _syncReceiveThread;

    private Thread _dispatcherThread;

    private int _unsentCompletions = 0;
    
    private int _lastTransferId = 0;

    private MessageImpl _currentMsg = null;
    
    protected MessageConsumerImpl(String consumerId, SessionImpl ssn, DestinationImpl dest, String selector,
            boolean noLocal, boolean browseOnly, AcknowledgeMode ackMode) throws JMSException
    {
        this(consumerId, ssn, dest, selector, noLocal, browseOnly, ackMode, null);
    }

    protected MessageConsumerImpl(String consumerId, SessionImpl ssn, DestinationImpl dest, String selector,
            boolean noLocal, boolean browseOnly, AcknowledgeMode ackMode, String subscriptionQueue) throws JMSException
    {
        _session = ssn;
        _dest = dest;
        _ackMode = ackMode;
        _selector = selector;
        _noLocal = noLocal;
        _consumerId = consumerId;
        _subscriptionQueue = subscriptionQueue;
        _capacity = AddressResolution.evaluateCapacity(_session.getConnection().getConfig().getMaxPrefetch(), _dest,
                CheckMode.RECEIVER);
        _localQueue = new LinkedBlockingQueue<MessageImpl>(_capacity == 0 ? 1 : _capacity);

        switch (ackMode)
        {
        case TRANSACTED:
        case CLIENT_ACK:
        case DUPS_OK:
            // TODO we may want to revisit this for perf reasons.
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
            _subscriptionQueue = AddressResolution.verifyAndCreateSubscription(this);
            setMessageFlowMode();
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
        return _noLocal;
    }

    protected DestinationImpl getDestination()
    {
        return _dest;
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException
    {
        checkPreConditions();
        if (_msgListener != null && _session.getConnection().isStarted())
        {
            throw new IllegalStateException("Message delivery is in progress with another listener");
        }

        if (listener != null)
        {
            stopMessageDelivery();
            _msgListener = listener;

            if (!_localQueue.isEmpty())
            {
                // TODO should we release them or should we serve them to the
                // listener?
            }

            if (isPrefetchDisabled())
            {
                setMessageCredit(1);
            }

            startMessageDelivery();
        }
    }

    @Override
    public void close() throws JMSException
    {
        closeImpl(true, true);
    }

    /**
     * The following are the steps involved.
     * 
     * 1. Consumer is removed from the session to prevent further delivery. Due
     * to the way ConcurrentHashMap works, the removal may not be immediately
     * visible. Therefore messageReceive will check for close and release any
     * messages given to it, after it was closed.
     * 
     * 2. Message delivery from this consumer is marked as stopped.The closing
     * thread will then await completion, if message delivery is in progress.
     * 
     * 3. A consumer may already be in a stopped state (Ex due to
     * connection.stop) before the close was called, and the dispatcher thread
     * maybe waiting on the stopped condition. The thread is woken up. The
     * subsequent check for close will release the message and return.
     * 
     * 4. If sendClose == true, protocol commands will be issued to cancel the
     * subscription, release messages in it's local queue and replay buffer
     * (unacked) and delete bindings and nodes as specified in the address. We
     * await the completion of these commands.
     * 
     * @param sendClose
     *            : Whether to send protocol close.
     * @param unregister
     *            : Whether to unregister from the session.
     */
    void closeImpl(boolean sendClose, boolean unregister) throws JMSException
    {
        if (!_closed.get())
        {
            _closed.set(true);

            if (unregister)
            {
                _session.removeConsumer(this);
            }

            stopMessageDelivery();

            if (sendClose)
            {
                closeConsumer();
            }
        }
    }

    @Override
    public MessageImpl receive() throws JMSException
    {
        preSyncReceiveCheck();
        _msgDeliveryStopped.waitUntilFalse();
        MessageImpl m = receiveImpl(0);
        return m;
    }

    @Override
    public MessageImpl receive(long timeout) throws JMSException
    {
        preSyncReceiveCheck();
        long remaining = timeout;
        try
        {
            remaining = _msgDeliveryStopped.waitUntilFalse(remaining);
        }
        catch (ConditionManagerTimeoutException e)
        {
           // Time out, return null.
            return null;
        }
        return receiveImpl(remaining);
    }

    @Override
    public MessageImpl receiveNoWait() throws JMSException
    {
        preSyncReceiveCheck();
        if (_msgDeliveryStopped.getCurrentValue())
        {
            return null;
        }
        return receiveImpl(-1L);
    }

    @Override
    public String toString()
    {
        return "ID:" + _consumerId + "delivery-in-progress:" + _msgDeliveryInProgress.getCurrentValue()
                + ", delivery-stopped:" + _msgDeliveryStopped.getCurrentValue() + ", Dest:" + _dest.getAddress();
    }

    MessageImpl receiveImpl(long timeout) throws JMSException
    {
        MessageImpl m = null;
        try
        {
            checkClosed();
            _syncReceiveThread = Thread.currentThread();

            if (_localQueue.isEmpty() && isPrefetchDisabled())
            {
                setMessageCredit(1);
                sendMessageFlush();
            }
            try
            {
                if (timeout > 0)
                {
                    m = _localQueue.poll(timeout, TimeUnit.MILLISECONDS);
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

            checkClosed();

            if (m != null)
            {
                preDeliver(m);
                postDeliver(m);
            }
        }
        finally
        {
            _syncReceiveThread = null;
        }
        return m;
    }

    void messageReceived(MessageImpl m)
    {
        if (isClosed())
        {
            releaseMessageAndLogException(m);
            return;
        }

        _msgDeliveryStopped.waitUntilFalse();

        if (isClosed())
        {
            releaseMessageAndLogException(m);
            return;
        }
        else if (_msgDeliveryStopped.getCurrentValue())
        {
            // We may have been woken up from the wait, but delivery is still
            // stopped.
            _session.requeueMessage(m);
        }
        else
        {
            _msgDeliveryInProgress.setValueAndNotify(true);
        }

        try
        {
            if (_msgListener != null)
            {
                _dispatcherThread = Thread.currentThread();
                _currentMsg = m;
                preDeliver(m);
                try
                {
                    _msgListener.onMessage(m);
                }
                catch (Exception e)
                {
                    //To protect the dispatcher thread from existing due to unhandled application errors.
                    _logger.warn(e, "Exception thrown from onMessage()");
                    fullDump(false);
                }

                try
                {
                    if (isPrefetchDisabled())
                    {
                        setMessageCredit(1);
                    }
                    try
                    {
                        postDeliver(m);
                    }
                    catch (Exception e)
                    {
                        fullDump(true);
                    }
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
            _currentMsg = null;
            _dispatcherThread = null;
            _msgDeliveryInProgress.setValueAndNotify(false);
        }
    }

    void preDeliver(MessageImpl m)
    {
        switch (_ackMode)
        {
        case TRANSACTED:
        case CLIENT_ACK:
        case DUPS_OK:
            _replayQueue.add(m);
            break;        
        default: // AUTO_ACK, NO_ACK
            break;
        }
    }

    void postDeliver(MessageImpl m) throws JMSException
    {
        sendCompleted(m);
        switch (_ackMode)
        {
        case AUTO_ACK:
            if (!_redeliverCurrentMsg.get())
            {
                sendMessageAccept(m, true);
            }
            break;
        case DUPS_OK:
            if (_replayQueue.size() >= _capacity * 0.8)
            {
                sendMessageAccept(false);
            }
            break;
        default:
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
            _session.getAMQPSession().messageStop(_consumerId, Option.UNRELIABLE);
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
        if (Thread.currentThread() != _dispatcherThread)
        {
            _msgDeliveryStopped.setValueAndNotify(true);
            _msgDeliveryStopped.wakeUpAndReturn();
            waitForInProgressDeliveriesToStop();
        }
    }

    /*
     * Not waiting for delivery to be stopped, as the dispatcher thread maybe
     * blocked inside onMessage() on some application logic or on the
     * failoverInProgress condition if it has called commit(), recover() etc...
     */
    void preFailover()
    {
        _msgDeliveryStopped.setValueAndNotify(true);
        _msgDeliveryStopped.wakeUpAndReturn();
        if (_msgListener == null)
        {
            waitForInProgressDeliveriesToStop();
        }
        clearLocalAndReplayQueue();
    }

    void postFailover() throws JMSException
    {        
        createSubscription();
        if (_session.isStarted())
        {
            start();
        }
    }

    List<MessageImpl> getUnackedMessagesForRequeue() throws JMSException
    {
        ArrayList<MessageImpl> tmp = new ArrayList<MessageImpl>(_localQueue.size() + _replayQueue.size());
        for (Iterator<MessageImpl> it = _replayQueue.iterator(); it.hasNext();)
        {
            MessageImpl m = it.next();
            m.setJMSRedelivered(true);
            tmp.add(m);
            it.remove();
        }
        _replayQueue.clear();
        _localQueue.drainTo(tmp);
        return tmp;
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
            _replayQueue.clear();

            if (unacked.size() > 0)
            {
                _session.getAMQPSession().messageRelease(unacked, Option.SET_REDELIVERED);
            }

            RangeSet prefetched = RangeSetFactory.createRangeSet();
            for (MessageImpl m : _localQueue)
            {
                prefetched.add(m.getTransferId());
            }
            _localQueue.clear();

            if (prefetched.size() > 0)
            {
                _session.getAMQPSession().messageRelease(prefetched);
            }
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
        if (m.getId() > _lastTransferId)
        {
            _lastTransferId = m.getId();
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
                _lastTransferId = 0;
            }
        }
    }

    void sendMessageAccept(MessageImpl m, boolean sync) throws JMSException
    {
        try
        {
            RangeSet range = RangeSetFactory.createRangeSet();
            range.add(m.getTransferId());
            _session.sendAcknowledgements(range, sync);
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
            if (_replayQueue.size() > 0)
            {
                RangeSet rangeSet = RangeSetFactory.createRangeSet();
                getUnackedMessageIds(rangeSet);
                _session.sendAcknowledgements(rangeSet, sync);
            }
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Exception when trying to send message accepts", e);
        }
    }

    void getUnackedMessageIds(final RangeSet range) throws JMSException
    {
        getUnackedMessageIds(range, Integer.MAX_VALUE);
    }

    void getUnackedMessageIds(final RangeSet range, int threshold) throws JMSException
    {        
        try
        {
            Iterator<MessageImpl> it = _replayQueue.iterator();
            while (it.hasNext())
            {
                MessageImpl m = it.next();
                if (m.getTransferId() <= threshold)
                {
                    range.add(m.getTransferId());
                    it.remove();     
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            throw ExceptionHelper.toJMSException("Exception when trying to send message accepts", e);
        }
    }

    void setRedeliverCurrentMessage(boolean b)
    {
        _redeliverCurrentMsg.set(b);
    }

    MessageImpl getCurrentMessage()
    {
        return _currentMsg;
    }

    void clearLocalAndReplayQueue()
    {
        _completions.clear();
        _unsentCompletions = 0;
        _localQueue.clear();
        _replayQueue.clear();
    }

    boolean isClosed()
    {
        return _closed.get();
    }

    void markAsClosing()
    {
        _closing.set(true);
    }

    protected SessionImpl getSession()
    {
        return _session;
    }

    protected AcknowledgeMode getAckMode()
    {
        return _ackMode;
    }

    protected String getConsumerId()
    {
        return _consumerId;
    }

    protected void setSubscriptionQueue(String queueName)
    {
        _subscriptionQueue = queueName;
    }

    protected String getSubscriptionQueue()
    {
        return _subscriptionQueue;
    }

    protected void setMessageFlowMode() throws JMSException
    {
        try
        {
            if (_capacity == 0)
            {
                // No prefetch case
                _session.getAMQPSession().messageSetFlowMode(_consumerId, MessageFlowMode.CREDIT);
            }
            else
            {
                _session.getAMQPSession().messageSetFlowMode(_consumerId, MessageFlowMode.WINDOW);
            }
            _session.getAMQPSession().messageFlow(_consumerId, MessageCreditUnit.BYTE, 0xFFFFFFFF, Option.UNRELIABLE);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error setting message flow mode.", e);
        }
    }

    protected void closeConsumer() throws JMSException
    {
        cancelSubscription();
        releaseMessages();
        AddressResolution.cleanupForConsumer(_session, _dest, _subscriptionQueue);
        _session.getAMQPSession().sync();
    }

    protected void cancelSubscription() throws JMSException
    {
        try
        {
            _session.getAMQPSession().messageCancel(_consumerId);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error cancelling subscription", e);
        }
    }

    private void setMessageCredit(int credit) throws JMSException
    {
        try
        {
            if (_capacity > 0)
            {
                _session.getAMQPSession()
                        .messageFlow(_consumerId, MessageCreditUnit.MESSAGE, credit, Option.UNRELIABLE);
            }
            else if (isMessageListener())
            {
                _session.getAMQPSession().messageFlow(_consumerId, MessageCreditUnit.MESSAGE, 1, Option.UNRELIABLE);
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

    private void sendMessageFlush() throws JMSException
    {
        try
        {
            _session.getAMQPSession().messageFlush(_consumerId);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error sending message.flush", e);
        }
    }

    // TODO Move this and all other syncs to the session.
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

    private void releaseMessageAndLogException(MessageImpl m)
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

    private void preSyncReceiveCheck() throws JMSException
    {
        checkClosed();
        if (_msgListener != null)
        {
            throw new IllegalStateException("A listener has already been set.");
        }
    }

    private void checkPreConditions() throws JMSException
    {
        checkClosed();
        _session.checkPreConditions();
    }

    private void checkClosed() throws JMSException
    {
        if (_closing.get() || _closed.get())
        {
            throw new IllegalStateException("Consumer is closed");
        }
    }

    private boolean isMessageListener()
    {
        return _msgListener != null;
    }

    private boolean isPrefetchDisabled()
    {
        return _capacity == 0;
    }
    
    private void fullDump(boolean exit)
    {
        System.out.println("****************************************************");
        System.out.println("************ Consumer Dump **************");
        System.out.println("Dest " + _dest);
        
        RangeSet range = RangeSetFactory.createRangeSet();
        for (MessageImpl m: _replayQueue)
        {
            range.add(m.getId());
        }
        System.out.println("ReplayQueue " + range);
        
        
        range = RangeSetFactory.createRangeSet();
        for (MessageImpl m: _localQueue)
        {
            range.add(m.getId());
        }
        System.out.println("LocalQueue " + range);
        
        System.out.println("_unsentCompletions " + _unsentCompletions);
        System.out.println("Completions " + _completions);
        
        System.out.println("****************************************************");
        
        /*if(exit)
        {
            System.exit(0);
        }*/
    }
}