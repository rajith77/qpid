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
import javax.jms.TemporaryQueue;
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
import org.apache.qpid.transport.RangeSet;
import org.apache.qpid.transport.RangeSetFactory;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.ConditionManager;
import org.apache.qpid.util.ExceptionHelper;
import org.apache.qpid.util.MessageFactorySupport;

public class SessionImpl implements Session, QueueSession, TopicSession
{
    private static final Logger _logger = Logger.get(SessionImpl.class);

    private static final AtomicInteger _consumerId = new AtomicInteger();

    private static Timer timer = new Timer("ack-flusher", true);

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
                    ssn.flushPendingAcknowledgements();
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

    private final long _maxAckDelay = Long.getLong(ClientProperties.QPID_SESSION_MAX_ACK_DELAY,
            ClientProperties.DEFAULT_SESSION_MAX_ACK_DELAY);

    private final List<MessageProducerImpl> _producers = new CopyOnWriteArrayList<MessageProducerImpl>();

    private final Map<String, MessageConsumerImpl> _consumers = new ConcurrentHashMap<String, MessageConsumerImpl>(2);

    private final Map<String, DurableTopicSubscriberImpl> _durableSubs = new ConcurrentHashMap<String, DurableTopicSubscriberImpl>(2);

    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private final AtomicBoolean _failedOverDirty = new AtomicBoolean(false);

    private final ConditionManager _msgDeliveryInProgress = new ConditionManager(false);

    private final ConditionManager _msgDeliveryStopped = new ConditionManager(true);

    private final AtomicBoolean _closeFromOnMessage = new AtomicBoolean(false);

    private final MessageFactory _messageFactory;

    private final Map<Integer, MessageTransfer> _replayQueue;

    private TimerTask _flushTask = null;

    private org.apache.qpid.transport.Session _amqpSession;

    protected SessionImpl(ConnectionImpl conn, int ackMode) throws JMSException
    {
        _conn = conn;
        _ackMode = AcknowledgeMode.getAckMode(ackMode);
        createProtocolSession();

        if (AcknowledgeMode.DUPS_OK == _ackMode && _maxAckDelay > 0)
        {
            _flushTask = new Flusher(this);
            timer.schedule(_flushTask, new Date(), _maxAckDelay);
        }

        // Message factory could be a connection property if need be.
        _messageFactory = MessageFactorySupport.getMessageFactory(null);

        _replayQueue = new ConcurrentHashMap<Integer, MessageTransfer>(Integer.getInteger(
                ClientProperties.QPID_SESSION_REPLAY_QUEUE_CAPACITY,
                ClientProperties.DEFAULT_SESSION_REPLAY_QUEUE_CAPACITY));
    }

    private void createProtocolSession() throws JMSException
    {
        try
        {
            _amqpSession = _conn.getAMQPConnection().createSession(1);
        }
        catch (ConnectionException ce)
        {
            ExceptionHelper.toJMSException("Error creating protocol session", ce);
        }

        _amqpSession.setSessionListener(_conn);

        try
        {
            if (_ackMode == AcknowledgeMode.TRANSACTED)
            {
                _amqpSession.txSelect();
                _amqpSession.setTransacted(true);
            }
        }
        catch (SessionException se)
        {
            ExceptionHelper.toJMSException("Error marking protocol session as transacted", se);
        }
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
                _conn.removeSession(this, sendClose);
            }

            cancelTimerTask();

            stopMessageDelivery();
            _msgDeliveryStopped.wakeUpAndReturn();

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
                getAMQPSession().close();
                getAMQPSession().sync();
            }
        }
    }

    @Override
    public void commit() throws JMSException
    {
        checkClosed();
        checkTransactional();

        if (_failedOverDirty.get())
        {
            rollback();

            throw new TransactionRolledBackException(
                    "Connection failover has occured with uncommitted transaction activity."
                            + "The transaction was rolled back.");
        }

        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.sendMessageAccept(false);
        }

        try
        {
            _amqpSession.setAutoSync(true);
            _amqpSession.txCommit();
            _amqpSession.setAutoSync(false);
        }
        catch (SessionException se)
        {
            closeImpl(false, false);
            throw ExceptionHelper.toJMSException("Commit failed due to error", se);
        }
    }

    @Override
    public void rollback() throws JMSException
    {
        checkClosed();
        checkTransactional();

        stopMessageDelivery();

        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.stopMessageDelivery();
        }

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

        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.requeueUnackedMessages();
            cons.startMessageDelivery();
        }

    }

    @Override
    public void recover() throws JMSException
    {
        checkClosed();
        checkNotTransactional();

        stopMessageDelivery();

        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.stopMessageDelivery();
        }

        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.requeueUnackedMessages();
            cons.startMessageDelivery();
        }
        
        startMessageDelivery();
    }

    void messageReceived(MessageImpl m)
    {
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
        else
        {

            _msgDeliveryInProgress.setValueAndNotify(true);
        }
        try
        {
            MessageConsumerImpl cons = _consumers.get(m.getConsumerId());
            if (cons != null)
            {
                cons.messageReceived(m);
            }
            else
            {
                releaseMessageAndLogException(m);
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

    void start() throws JMSException
    {
        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.start();
        }
        startMessageDelivery();
    }

    void stop() throws JMSException
    {
        stopMessageDelivery();

        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.stop();
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

        stopMessageDelivery();

        for (MessageProducerImpl prod : _producers)
        {
            prod.stopMessageSender();
        }
        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.stopMessageDelivery();
        }
    }

    void postFailover() throws JMSException
    {
        createProtocolSession();
        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.createSubscription();            
            cons.startMessageDelivery();
        }
        for (MessageProducerImpl prod : _producers)
        {
            prod.verifyDestinationForProducer();
            prod.startMessageSender();
        }

        startMessageDelivery();
    }

    @Override
    public MessageProducer createProducer(Destination dest) throws JMSException
    {
        if (!(dest instanceof DestinationImpl))
        {
            throw new InvalidDestinationException("Invalid destination class " + dest.getClass().getName());
        }
        MessageProducerImpl prod = new MessageProducerImpl(this, (DestinationImpl)dest);
        _producers.add(prod);
        return prod;
    }

    @Override
    public MessageConsumer createConsumer(Destination dest) throws JMSException
    {
        return createConsumer(dest, null);
    }

    @Override
    public MessageConsumer createConsumer(Destination dest, String selector) throws JMSException
    {
        return createConsumer(dest, selector, false);
    }

    @Override
    public MessageConsumer createConsumer(Destination dest, String selector, boolean noLocal) throws JMSException
    {
        if (!(dest instanceof DestinationImpl))
        {
            throw new InvalidDestinationException("Invalid destination class " + dest.getClass().getName());
        }

        String tag = String.valueOf(_consumerId.incrementAndGet());
        MessageConsumerImpl cons = new MessageConsumerImpl(tag, this, (DestinationImpl)dest, selector, noLocal, false, _ackMode);
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
        if (!(topic instanceof TopicImpl))
        {
            throw new InvalidDestinationException("Invalid Topic Class" + (topic == null? ": Null" : topic.getClass().getName()));
        }
        
        if ((topic instanceof TemporaryTopic) && ((TemporaryTopicImpl)topic).getSession() != this)
        {
            throw new InvalidDestinationException("Cannot use a temporary topic created from a different session");
        }

        if (_durableSubs.containsKey(name))
        {
            DurableTopicSubscriberImpl topicSub = _durableSubs.get(name);
            
            if ( topic.equals(topicSub.getTopic()) && 
                 ( (selector == null && topicSub.getMessageSelector() == null) ||
                   (selector != null && selector.equals(topicSub.getMessageSelector()))       
                 )
               )
            {
                throw new IllegalStateException("Already subscribed to topic [" + topic + "] with subscription name " + name
                    + (selector != null ? " and selector " + selector : ""));
            }
            else
            {
                topicSub.close();
            }
        }
        
        String tag = String.valueOf(_consumerId.incrementAndGet());
        DurableTopicSubscriberImpl topicSub = new DurableTopicSubscriberImpl(name, tag, this, (TopicImpl)topic, selector, noLocal, false, _ackMode);
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
        if (_durableSubs.containsKey(name))
        {
            _durableSubs.get(name).close();
        }
        else
        {
            throw new InvalidDestinationException("Invalid subscription name. No subscriptions is associated with this name");
        }
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicPublisher createPublisher(Topic topic) throws JMSException
    {
        if (!(topic instanceof TopicImpl))
        {
            throw new InvalidDestinationException("Invalid Topic Class" + topic.getClass().getName());
        }
        TopicPublisherImpl prod = new TopicPublisherImpl(this, (TopicImpl)topic);
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
        if (!(topic instanceof TopicImpl))
        {
            throw new InvalidDestinationException("Invalid Topic Class" + topic.getClass().getName());
        }

        String tag = String.valueOf(_consumerId.incrementAndGet());
        TopicSubscriberImpl cons = new TopicSubscriberImpl(tag, this, (TopicImpl)topic, selector, noLocal, false, _ackMode);
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
        return createReceiver(queue);
    }

    @Override
    public QueueReceiver createReceiver(Queue queue, String selector) throws JMSException
    {
        if (!(queue instanceof QueueImpl))
        {
            throw new InvalidDestinationException("Invalid Queue Class" + queue.getClass().getName());
        }

        String tag = String.valueOf(_consumerId.incrementAndGet());
        QueueReceiverImpl cons = new QueueReceiverImpl(tag, this, (QueueImpl)queue, selector, false, false, _ackMode);
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
        if (!(queue instanceof QueueImpl))
        {
            throw new InvalidDestinationException("Invalid Queue Class" + queue.getClass().getName());
        }

        QueueSenderImpl prod = new QueueSenderImpl(this, (QueueImpl)queue);
        _producers.add(prod);
        return prod;
    }

    @Override
    public Message createMessage() throws JMSException
    {
        return _messageFactory.createMessage();
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException
    {
        return _messageFactory.createBytesMessage();
    }

    @Override
    public TextMessage createTextMessage() throws JMSException
    {
        return _messageFactory.createTextMessage();
    }

    @Override
    public TextMessage createTextMessage(String txt) throws JMSException
    {
        TextMessage msg = createTextMessage();
        msg.setText(txt);
        return msg;
    }

    @Override
    public MapMessage createMapMessage() throws JMSException
    {
        return _messageFactory.createMapMessage();
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException
    {
        return _messageFactory.createObjectMessage();
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable serilizable) throws JMSException
    {
        ObjectMessage msg = createObjectMessage();
        msg.setObject(serilizable);
        return msg;
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException
    {
        return _messageFactory.createStreamMessage();
    }

    @Override
    public Queue createQueue(String queue) throws JMSException
    {
        return new QueueImpl(queue);
    }

    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Topic createTopic(String topic) throws JMSException
    {
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

    Thread getDispatcherThread()
    {
        return null;
    }

    void checkClosed() throws JMSException
    {
        if (_closed.get())
        {
            throw new IllegalStateException("Session is closed");
        }
    }

    void removeProducer(MessageProducerImpl prod)
    {
        _producers.remove(prod);
    }

    void removeConsumer(MessageConsumerImpl cons)
    {
        _consumers.remove(cons);
    }

    org.apache.qpid.transport.Session getAMQPSession()
    {
        return _amqpSession;
    }

    void acknowledgeMesages()
    {

    }

    boolean isClosed()
    {
        return _closed.get();
    }

    void addToReplayQueue(MessageTransfer msg)
    {
        _replayQueue.put(msg.getId(), msg);
    }

    private void flushPendingAcknowledgements() throws JMSException
    {
        for (MessageConsumerImpl consumer : _consumers.values())
        {
            consumer.sendMessageAccept(true);
        }
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
            _amqpSession.messageRelease(range);
        }
        catch (Exception e)
        {
            _logger.warn(e, "Error trying to release message for closed consumer");
        }
    }

    private void startMessageDelivery()
    {
        _msgDeliveryStopped.setValueAndNotify(false);
    }
    
    private void stopMessageDelivery()
    {
        _msgDeliveryStopped.setValueAndNotify(true);
        _msgDeliveryInProgress.waitUntilFalse();
    }

    private boolean isStarted()
    {
        return _conn.isStarted();
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
        checkClosed();
        throw new JmsNotImplementedException();
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException
    {
        checkClosed();
        throw new JmsNotImplementedException();
    }
}