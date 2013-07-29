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

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.jms.TransactionRolledBackException;

import org.apache.qpid.amqp_0_10.jms.MessageFactory;
import org.apache.qpid.client.JmsNotImplementedException;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.transport.ConnectionException;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.RangeSet;
import org.apache.qpid.transport.RangeSetFactory;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.ConditionManager;
import org.apache.qpid.util.ExceptionHelper;

public class SessionImpl implements Session, QueueSession, TopicSession
{
    private static final Logger _logger = Logger.get(SessionImpl.class);

    private static final AtomicInteger _consumerId = new AtomicInteger();

    private static final Timer timer = new Timer("ack-flusher", true);

    private static class Flusher extends TimerTask
    {
        private WeakReference<SessionImpl> _session;

        public Flusher(SessionImpl session)
        {
            _session = new WeakReference<SessionImpl>(session);
        }

        public void run()
        {
            SessionImpl ssn = _session.get();
            if (ssn == null)
            {
                cancel();
            }
            else
            {
                try
                {
                    ssn.acknowledgeUpTo(Integer.MAX_VALUE, false);
                }
                catch (JMSException e)
                {
                    _logger.warn(e, "Error flushing pending acknowledgements");
                }
            }
        }
    }

    private final ConnectionImpl _conn;

    private final AcknowledgeMode _ackMode;

    private final long MAX_ACK_DELAY = Long.getLong(ClientProperties.QPID_SESSION_MAX_ACK_DELAY,
            ClientProperties.DEFAULT_SESSION_MAX_ACK_DELAY);

    private final List<MessageProducerImpl> _producers = new CopyOnWriteArrayList<MessageProducerImpl>();

    private final Map<String, MessageConsumerImpl> _consumers = new ConcurrentHashMap<String, MessageConsumerImpl>(2);

    private final Map<String, DurableTopicSubscriberImpl> _durableSubs = new ConcurrentHashMap<String, DurableTopicSubscriberImpl>(
            2);

    private final List<TemporaryQueue> _tempQueues = new ArrayList<TemporaryQueue>();

    private final AtomicBoolean _closing = new AtomicBoolean(false);

    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private final AtomicBoolean _failedOverDirty = new AtomicBoolean(false);

    private final ConditionManager _msgDeliveryInProgress = new ConditionManager(false);

    private final ConditionManager _msgDeliveryStopped = new ConditionManager(true);

    private final MessageFactory _messageFactory;

    private final Map<Integer, MessageTransfer> _replayQueue;

    private TimerTask _flushTask = null;

    private org.apache.qpid.transport.Session _amqpSession;

    private SessionException _exception;

    private Thread _dispatcherThread;

    protected SessionImpl(ConnectionImpl conn, int ackMode) throws JMSException
    {
        _conn = conn;
        _ackMode = AcknowledgeMode.getAckMode(ackMode);
        createProtocolSession();

        if (AcknowledgeMode.DUPS_OK == _ackMode && MAX_ACK_DELAY > 0)
        {
            _flushTask = new Flusher(this);
            timer.schedule(_flushTask, new Date(), MAX_ACK_DELAY);
        }

        _messageFactory = _conn.getMessageFactory();

        _replayQueue = new ConcurrentHashMap<Integer, MessageTransfer>(Integer.getInteger(
                ClientProperties.QPID_SESSION_REPLAY_QUEUE_CAPACITY,
                ClientProperties.DEFAULT_SESSION_REPLAY_QUEUE_CAPACITY));
    }

    private void createProtocolSession() throws JMSException
    {
        System.out.println("================================");
        System.out.println("Creating session " + _ackMode);
        System.out.println("================================");
        try
        {
            // Remove any old associations if present.
            _conn.removeSession(this, false);
            _amqpSession = _conn.getAMQPConnection().createSession(1);
            _conn.addSession(this);
        }
        catch (ConnectionException ce)
        {
            throw ExceptionHelper.toJMSException("Error creating protocol session", ce);
        }

        try
        {
            System.out.println("================================");
            System.out.println("Going to mark session transacted");
            System.out.println("================================");
            if (_ackMode == AcknowledgeMode.TRANSACTED)
            {
                _amqpSession.txSelect();
                _amqpSession.setTransacted(true);
                System.out.println("================================");
                System.out.println("Marked session transacted");
                System.out.println("================================");
            }
        }
        catch (SessionException se)
        {
            throw ExceptionHelper.toJMSException("Error marking protocol session as transacted", se);
        }

        System.out.println("================================");
        System.out.println("Finish session creation");
        System.out.println("================================");

        _amqpSession.setSessionListener(_conn);
    }

    @Override
    public void close() throws JMSException
    {
        closeImpl(true, true);
    }

    /**
     * @param sendClose
     *            : Whether to send protocol close.
     * @param unregister
     *            : Whether to unregister from the connection.
     */
    void closeImpl(boolean sendClose, boolean unregister) throws JMSException
    {
        if (!_closed.get())
        {
            _closed.set(true);

            if (unregister)
            {
                _conn.removeSession(this, _dispatcherThread == Thread.currentThread());
            }

            cancelTimerTask();

            stopMessageDelivery();

            for (MessageProducerImpl prod : _producers)
            {
                prod.closeImpl(sendClose, false);
            }

            for (MessageConsumerImpl cons : _consumers.values())
            {
                cons.closeImpl(sendClose, false);
            }

            _consumers.clear();
            _producers.clear();

            if (sendClose)
            {
                for (TemporaryQueue tempQueue : _tempQueues)
                {
                    tempQueue.deleteQueue(false);
                }
                _tempQueues.clear();

                getAMQPSession().close();
            }

            _conn.startDispatcherForSession(this);
        }
    }

    @Override
    public void commit() throws JMSException
    {
        checkPreConditions();
        checkTransactional();

        if (_failedOverDirty.get())
        {
            rollback();

            throw new TransactionRolledBackException(
                    "Connection failover has occured with uncommitted transaction activity."
                            + "The transaction was rolled back.");
        }

        RangeSet rangeSet = RangeSetFactory.createRangeSet();
        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.getUnackedMessageIds(rangeSet);
        }
        sendAcknowledgements(rangeSet, false);

        try
        {
            _amqpSession.setAutoSync(true);
            _amqpSession.txCommit();
            _amqpSession.setAutoSync(false);
        }
        catch (SessionException se)
        {
            throw ExceptionHelper.toJMSException("Commit failed due to error", se);
        }
    }

    @Override
    public void rollback() throws JMSException
    {
        checkPreConditions();
        checkTransactional();

        stopMessageDelivery();

        List<MessageImpl> requeueList = new ArrayList<MessageImpl>();

        for (MessageConsumerImpl cons : _consumers.values())
        {
            if (Thread.currentThread() == _dispatcherThread)
            {
                cons.setRedeliverCurrentMessage(true);
            }

            cons.stopMessageDelivery();
            requeueList.addAll(cons.getUnackedMessagesForRequeue());

            MessageImpl currentMsg = cons.getCurrentMessage();
            if (Thread.currentThread() == _dispatcherThread && currentMsg != null)
            {
                currentMsg.setJMSRedelivered(true);
                requeueList.add(currentMsg);
            }
        }

        System.out.println("SessionImpl.rollback requeList " + requeueList);

        try
        {
            _amqpSession.setAutoSync(true);
            _amqpSession.txRollback();
            _amqpSession.setAutoSync(false);
        }
        catch (SessionException se)
        {
            closeImpl(false, false);
            throw ExceptionHelper.toJMSException("Rollback failed due to error", se);
        }

        if (_failedOverDirty.get())
        {
            _failedOverDirty.set(false);
        }

        requeueMessages(requeueList);
        sortDispatchQueue();

        for (MessageConsumerImpl cons : _consumers.values())
        {
            if (Thread.currentThread() == _dispatcherThread)
            {
                cons.setRedeliverCurrentMessage(false);
            }
            cons.startMessageDelivery();
        }

        startMessageDelivery();
    }

    @Override
    public void recover() throws JMSException
    {
        checkPreConditions();
        checkNotTransactional();

        if (!_conn.isStarted())
        {
            throw new IllegalStateException("The connection is currently stopped.");
        }

        stopMessageDelivery();

        List<MessageImpl> requeueList = new ArrayList<MessageImpl>();

        for (MessageConsumerImpl cons : _consumers.values())
        {
            if (Thread.currentThread() == _dispatcherThread)
            {
                cons.setRedeliverCurrentMessage(true);
            }

            cons.stopMessageDelivery();
            requeueList.addAll(cons.getUnackedMessagesForRequeue());

            MessageImpl currentMsg = cons.getCurrentMessage();
            if (Thread.currentThread() == _dispatcherThread && currentMsg != null)
            {
                currentMsg.setJMSRedelivered(true);
                requeueList.add(currentMsg);
            }
        }

        requeueMessages(requeueList);
        sortDispatchQueue();

        for (MessageConsumerImpl cons : _consumers.values())
        {
            if (Thread.currentThread() == _dispatcherThread)
            {
                cons.setRedeliverCurrentMessage(false);
            }
            cons.startMessageDelivery();
        }

        startMessageDelivery();
    }

    void messageReceived(MessageImpl m)
    {
        System.out.println("SessionImpl.messageReceived xxxx ");
        if (isClosed())
        {
            // drop the message on the floor.
            return;
        }
        _msgDeliveryStopped.waitUntilFalse();

        if (isClosed())
        {
            // drop the message on the floor.
            return;
        }

        if (_msgDeliveryStopped.getCurrentValue())
        {
            requeueMessage(m);
            return;
        }

        try
        {
            _msgDeliveryInProgress.setValueAndNotify(true);
            System.out.println("################# going to deliver to consumer _msgDeliveryInProgress : "
                    + _msgDeliveryInProgress.getCurrentValue());
            MessageConsumerImpl cons = _consumers.get(m.getConsumerId());
            if (cons != null)
            {
                _dispatcherThread = Thread.currentThread();
                cons.messageReceived(m);
            }
            else
            {
                releaseMessageAndLogException(m);
            }
        }
        finally
        {
            _dispatcherThread = null;
            _msgDeliveryInProgress.setValueAndNotify(false);
            System.out.println("################# finally _msgDeliveryInProgress : "
                    + _msgDeliveryInProgress.getCurrentValue());
        }
    }

    void start() throws JMSException
    {
        System.out.println("************************* Start called on consumer. " + _consumers);
        for (MessageConsumerImpl cons : _consumers.values())
        {
            System.out.println("************************* Start called on consumer.  Is closed " + cons.isClosed());
            if (!cons.isClosed())
            {
                cons.start();
            }
        }
        startMessageDelivery();
    }

    void stop() throws JMSException
    {
        stopMessageDelivery();

        for (MessageConsumerImpl cons : _consumers.values())
        {
            if (!cons.isClosed())
            {
                cons.stop();
            }
        }

        try
        {
            getAMQPSession().sync();
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error waiting for message stopped to complete", e);
        }
        // TODO is there a requirement to drain the queues and release messages
        // in internal queues ?
    }

    void preFailover() throws JMSException
    {
        if (_ackMode == AcknowledgeMode.TRANSACTED)
        {
            _failedOverDirty.set(true);
        }

        _msgDeliveryStopped.setValueAndNotify(true);

        for (MessageProducerImpl prod : _producers)
        {
            prod.preFailover();
        }

        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.preFailover();
        }
    }

    void postFailover() throws JMSException
    {
        createProtocolSession();
        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.postFailover();
        }
        for (MessageProducerImpl prod : _producers)
        {
            prod.postFailover();
        }

        startMessageDelivery();
        replayMessages();
    }

    @Override
    public MessageProducer createProducer(Destination dest) throws JMSException
    {
        checkPreConditions();
        verifyDestination(dest, true);
        MessageProducerImpl prod = new MessageProducerImpl(this, (DestinationImpl) dest);
        _producers.add(prod);
        return prod;
    }

    @Override
    public MessageConsumer createConsumer(Destination dest) throws JMSException
    {
        return createConsumer(dest, null, false);
    }

    @Override
    public MessageConsumer createConsumer(Destination dest, String selector) throws JMSException
    {
        return createConsumer(dest, selector, false);
    }

    @Override
    public MessageConsumer createConsumer(Destination dest, String selector, boolean noLocal) throws JMSException
    {
        checkPreConditions();
        verifyDestination(dest, false);
        String tag = String.valueOf(_consumerId.incrementAndGet());
        MessageConsumerImpl cons = new MessageConsumerImpl(tag, this, (DestinationImpl) dest, selector, noLocal, false,
                _ackMode);
        _consumers.put(tag, cons);

        if (isStarted())
        {
            cons.start();
        }

        return cons;
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException
    {
        return createDurableSubscriber(topic, name, null, false);
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String selector, boolean noLocal)
            throws JMSException
    {
        checkPreConditions();
        verifyTopic(topic, false);
        if (_durableSubs.containsKey(name))
        {
            DurableTopicSubscriberImpl topicSub = _durableSubs.get(name);

            if (topic.equals(topicSub.getTopic())
                    && ((selector == null && topicSub.getMessageSelector() == null) || (selector != null && selector
                            .equals(topicSub.getMessageSelector()))))
            {
                throw new IllegalStateException("Already subscribed to topic [" + topic + "] with subscription name "
                        + name + (selector != null ? " and selector " + selector : ""));
            }
            else
            {
                topicSub.close();
            }
        }

        String tag = String.valueOf(_consumerId.incrementAndGet());
        DurableTopicSubscriberImpl topicSub = new DurableTopicSubscriberImpl(name, tag, this, (TopicImpl) topic,
                selector, noLocal, false, _ackMode);
        _consumers.put(tag, topicSub);
        _durableSubs.put(name, topicSub);

        if (isStarted())
        {
            topicSub.start();
        }

        return topicSub;
    }

    @Override
    public void unsubscribe(String name) throws JMSException
    {
        checkPreConditions();
        if (_durableSubs.containsKey(name))
        {
            _durableSubs.get(name).close();
        }
        else
        {
            throw new InvalidDestinationException(
                    "Invalid subscription name. No subscriptions is associated with this name");
        }
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException
    {
        return createBrowser(queue, null);
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String selector) throws JMSException
    {
        checkPreConditions();
        // TODO Auto-generated method stub
        verifyQueue(queue, false);
        return null;
    }

    @Override
    public TopicPublisher createPublisher(Topic topic) throws JMSException
    {
        checkPreConditions();
        verifyTopic(topic, true);
        TopicPublisherImpl prod = new TopicPublisherImpl(this, (TopicImpl) topic);
        _producers.add(prod);
        return prod;
    }

    @Override
    public TopicSubscriber createSubscriber(Topic topic) throws JMSException
    {
        return createSubscriber(topic, null, false);
    }

    @Override
    public TopicSubscriber createSubscriber(Topic topic, String selector, boolean noLocal) throws JMSException
    {
        checkPreConditions();
        verifyTopic(topic, false);
        String tag = String.valueOf(_consumerId.incrementAndGet());
        TopicSubscriberImpl cons = new TopicSubscriberImpl(tag, this, (TopicImpl) topic, selector, noLocal, false,
                _ackMode);
        _consumers.put(tag, cons);

        if (isStarted())
        {
            cons.start();
        }

        return cons;
    }

    @Override
    public QueueReceiver createReceiver(Queue queue) throws JMSException
    {
        return createReceiver(queue, null);
    }

    @Override
    public QueueReceiver createReceiver(Queue queue, String selector) throws JMSException
    {
        checkPreConditions();
        verifyQueue(queue, false);
        String tag = String.valueOf(_consumerId.incrementAndGet());
        QueueReceiverImpl cons = new QueueReceiverImpl(tag, this, (QueueImpl) queue, selector, false, false, _ackMode);
        _consumers.put(tag, cons);

        if (isStarted())
        {
            cons.start();
        }

        return cons;
    }

    @Override
    public QueueSender createSender(Queue queue) throws JMSException
    {
        checkPreConditions();
        verifyQueue(queue, true);
        QueueSenderImpl prod = new QueueSenderImpl(this, (QueueImpl) queue);
        _producers.add(prod);
        return prod;
    }

    @Override
    public Message createMessage() throws JMSException
    {
        checkClosed();
        return _messageFactory.createMessage();
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException
    {
        checkClosed();
        return _messageFactory.createBytesMessage();
    }

    @Override
    public TextMessage createTextMessage() throws JMSException
    {
        checkClosed();
        return _messageFactory.createTextMessage();
    }

    @Override
    public TextMessage createTextMessage(String txt) throws JMSException
    {
        checkClosed();
        TextMessage msg = createTextMessage();
        msg.setText(txt);
        return msg;
    }

    @Override
    public MapMessage createMapMessage() throws JMSException
    {
        checkClosed();
        return _messageFactory.createMapMessage();
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException
    {
        checkClosed();
        return _messageFactory.createObjectMessage();
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable serilizable) throws JMSException
    {
        checkPreConditions();
        ObjectMessage msg = createObjectMessage();
        msg.setObject(serilizable);
        return msg;
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException
    {
        checkClosed();
        return _messageFactory.createStreamMessage();
    }

    @Override
    public Queue createQueue(String queue) throws JMSException
    {
        checkClosed();
        return new QueueImpl(queue);
    }

    @Override
    public javax.jms.TemporaryQueue createTemporaryQueue() throws JMSException
    {
        checkClosed();
        TemporaryQueueImpl queue = new TemporaryQueueImpl(this);
        _tempQueues.add(queue);
        return queue;
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        checkClosed();
        return new TemporaryTopicImpl(this);
    }

    @Override
    public Topic createTopic(String topic) throws JMSException
    {
        checkClosed();
        return new TopicImpl(topic);
    }

    @Override
    public int getAcknowledgeMode() throws JMSException
    {
        return AcknowledgeMode.getJMSAckMode(_ackMode);
    }

    @Override
    public boolean getTransacted() throws JMSException
    {
        return _ackMode == AcknowledgeMode.TRANSACTED;
    }

    ConnectionImpl getConnection()
    {
        return _conn;
    }

    void checkPreConditions() throws JMSException
    {
        if (!_closing.get() && !_closed.get())
        {
            _conn.waitForFailoverToComplete();
        }
        checkClosed();
    }

    void checkClosed() throws JMSException
    {
        if (_closing.get() || _closed.get())
        {
            if (_exception == null)
            {
                throw new IllegalStateException("Session is closed");
            }
            else
            {
                IllegalStateException ex = new IllegalStateException("Session is closed");
                ex.setLinkedException(_exception);
                ex.initCause(_exception);
                throw ex;
            }
        }
    }

    void markAsClosing()
    {
        _closing.set(true);
        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.markAsClosing();
        }

        for (MessageProducerImpl prod : _producers)
        {
            prod.markAsClosing();
        }
    }

    void removeProducer(MessageProducerImpl prod)
    {
        _producers.remove(prod);
    }

    void removeConsumer(MessageConsumerImpl cons)
    {
        System.out.println("xxxxxxxxxxxxxxxxxxxxxxx Remove called on consumer. " + _consumers);
        _consumers.remove(cons);
    }

    org.apache.qpid.transport.Session getAMQPSession()
    {
        return _amqpSession;
    }

    void acknowledgeUpTo(int transferId, boolean sync) throws JMSException
    {
        RangeSet rangeSet = RangeSetFactory.createRangeSet();
        for (MessageConsumerImpl consumer : _consumers.values())
        {
            consumer.getUnackedMessageIds(rangeSet);
        }
        sendAcknowledgements(rangeSet, sync);
    }

    boolean isClosed()
    {
        return _closed.get();
    }

    void addToReplayQueue(MessageTransfer msg)
    {
        if (_ackMode != AcknowledgeMode.TRANSACTED)
        {
            _replayQueue.put(msg.getId(), msg);
        }
    }

    void setException(SessionException e)
    {
        _exception = e;
    }

    void sendAcknowledgements(RangeSet rangeSet, boolean sync) throws JMSException
    {
        if (rangeSet.size() > 0)
        {
            try
            {
                _amqpSession.messageAccept(rangeSet);
                if (sync)
                {
                    _amqpSession.sync();
                }
            }
            catch (Exception e)
            {
                throw ExceptionHelper.toJMSException("Exception when trying to send message accepts", e);
            }
        }
    }

    void requeueMessage(MessageImpl m)
    {
        _conn.requeueMessage(this, m);
    }

    void requeueMessages(List<MessageImpl> list)
    {
        _conn.requeueMessages(this, list);
    }

    void sortDispatchQueue()
    {
        _conn.sortDispatchQueue(this);
    }

    void addTempQueue(TemporaryQueue dest)
    {
        _tempQueues.add(dest);
        _conn.addTempQueue(dest);
    }

    void removeTempQueue(TemporaryQueue dest)
    {
        _tempQueues.remove(dest);
        _conn.removeTempQueue(dest);
    }

    void replayMessages() throws JMSException
    {
        try
        {
            for (MessageTransfer transfer : _replayQueue.values())
            {
                _amqpSession.invoke(transfer);
            }
        }
        catch (Exception e)
        {
            System.out.println("Error replaying messages after failover");
            e.printStackTrace();
            throw ExceptionHelper.toJMSException("Error replaying messages after failover", e);
        }
    }

    void commandCompleted(int id)
    {
        _replayQueue.remove(id);
    }

    SessionException getException()
    {
        return _exception;
    }

    boolean isStarted()
    {
        return _conn.isStarted();
    }

    private void checkTransactional() throws JMSException
    {
        if (!getTransacted())
        {
            throw new IllegalStateException("Session must be transacted in order to perform this operation");
        }
    }

    private void checkNotTransactional() throws JMSException
    {
        if (getTransacted())
        {
            throw new IllegalStateException("This operation is not permitted on a transacted session");
        }
    }

    private void cancelTimerTask()
    {
        if (_flushTask != null)
        {
            _flushTask.cancel();
            _flushTask = null;
        }
    }

    private void releaseMessageAndLogException(MessageImpl m)
    {
        try
        {
            RangeSet range = RangeSetFactory.createRangeSet();
            range.add(m.getTransferId());
            _amqpSession.messageRelease(range, Option.SET_REDELIVERED);
        }
        catch (Exception e)
        {
            _logger.warn(e, "Error trying to release message for closed consumer");
        }
    }

    private void startMessageDelivery()
    {
        _msgDeliveryStopped.setValueAndNotify(false);
        _conn.startDispatcherForSession(this);
    }

    private void stopMessageDelivery()
    {
        if (Thread.currentThread() != _dispatcherThread)
        {
            _conn.stopDispatcherForSession(this);

            _msgDeliveryStopped.setValueAndNotify(true);
            _msgDeliveryStopped.wakeUpAndReturn();
            _msgDeliveryInProgress.waitUntilFalse();
        }
    }

    private void verifyDestination(Destination dest, boolean isNullAllowed) throws JMSException
    {
        if (dest == null && isNullAllowed)
        {
            return;
        }

        if (!(dest instanceof DestinationImpl))
        {
            throw new InvalidDestinationException("Invalid destination class "
                    + (dest == null ? ": Null" : dest.getClass().getName()));
        }
        verifyTempDestination(dest, "destination");
    }

    private void verifyTopic(Topic topic, boolean isNullAllowed) throws JMSException
    {
        if (topic == null && isNullAllowed)
        {
            return;
        }

        if (!(topic instanceof TopicImpl))
        {
            throw new InvalidDestinationException("Invalid Topic Class"
                    + (topic == null ? ": Null" : topic.getClass().getName()));
        }
        verifyTempDestination(topic, "topic");
    }

    private void verifyQueue(Queue queue, boolean isNullAllowed) throws JMSException
    {
        if (queue == null && isNullAllowed)
        {
            return;
        }

        if (!(queue instanceof QueueImpl))
        {
            throw new InvalidDestinationException("Invalid Queue Class"
                    + (queue == null ? ": Null" : queue.getClass().getName()));
        }
        verifyTempDestination(queue, "queue");
    }

    private void verifyTempDestination(Destination dest, String type) throws JMSException
    {
        if (dest instanceof TemporaryDestination)
        {
            TemporaryDestination tempDest = (TemporaryDestination) dest;

            if (tempDest.getSession() != this)
            {
                throw new InvalidDestinationException("Cannot use a temporary " + type
                        + " created from a different session");
            }

            if (tempDest.isDeleted())
            {
                throw new InvalidDestinationException("The temporary " + type + " is deleted and cannot be used");
            }
        }
    }

    // --------------- Unsupported Methods -------------
    @Override
    public void run()
    {
        throw new java.lang.UnsupportedOperationException("This operation is not supported");
    }

    @Override
    public MessageListener getMessageListener() throws JMSException
    {
        checkPreConditions();
        throw new JmsNotImplementedException();
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException
    {
        checkPreConditions();
        throw new JmsNotImplementedException();
    }
}