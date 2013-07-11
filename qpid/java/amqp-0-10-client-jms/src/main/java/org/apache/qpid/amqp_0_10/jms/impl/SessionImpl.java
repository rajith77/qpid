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
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.ExceptionHelper;
import org.apache.qpid.util.MessageFactorySupport;

public class SessionImpl implements Session, QueueSession, TopicSession
{
    private static final Logger _logger = Logger.get(SessionImpl.class);

    private static final AtomicInteger _consumerTag = new AtomicInteger();

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

    private org.apache.qpid.transport.Session _amqpSession;

    private final ConnectionImpl _conn;

    private final AcknowledgeMode _ackMode;

    private final long _maxAckDelay = Long.getLong(ClientProperties.QPID_SESSION_MAX_ACK_DELAY,
            ClientProperties.DEFAULT_SESSION_MAX_ACK_DELAY);

    private final List<MessageProducerImpl> _producers = new CopyOnWriteArrayList<MessageProducerImpl>();

    private final Map<String, MessageConsumerImpl> _consumers = new ConcurrentHashMap<String, MessageConsumerImpl>(2);

    private final AtomicBoolean _closed = new AtomicBoolean(false);

    private final AtomicBoolean _failedOverDirty = new AtomicBoolean(false);

    private final MessageFactory _messageFactory;

    private final Map<Integer, MessageTransfer> _replayQueue;

    private TimerTask _flushTask = null;

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
     * @param sendClose  : Whether to send protocol close.
     * @param unregister : Whether to unregister from the connection.
     */
    void closeImpl(boolean sendClose, boolean unregister) throws JMSException
    {
        if (!_closed.get())
        {
            _closed.set(true);
            cancelTimerTask();
            for (MessageProducerImpl prod : _producers)
            {
                prod.closeImpl(sendClose, false);
            }
            _consumers.clear();
            
            for (MessageConsumerImpl cons : _consumers.values())
            {
                cons.closeImpl(sendClose, false);
            }            
            _producers.clear();
            
            if (sendClose)
            {
                getAMQPSession().close();
                getAMQPSession().sync();
            }
            
            if (unregister)
            {
                _conn.removeSession(this);
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

            throw new TransactionRolledBackException("Connection failover has occured with uncommitted transaction activity." +
                                                     "The transaction was rolled back.");
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
            closeImpl(false,false);
            throw ExceptionHelper.toJMSException("Commit failed due to error", se);
        }
    }

    @Override
    public void rollback() throws JMSException
    {
        checkClosed();
        checkTransactional();

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
            closeImpl(false,false);
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

        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.stopMessageDelivery();
        }

        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.requeueUnackedMessages();
            cons.startMessageDelivery();
        }
    }

    void start() throws JMSException
    {
        for (MessageConsumerImpl cons : _consumers.values())
        {
            cons.start();
        }
    }

    void stop() throws JMSException
    {
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

        for (MessageProducerImpl prod: _producers)
        {
            prod.stopMessageSender();
        }
        for (MessageConsumerImpl cons: _consumers.values())
        {
            cons.stopMessageDelivery();
        }
    }
    
    void postFailover() throws JMSException
    {
        for (MessageConsumerImpl cons: _consumers.values())
        {
            cons.createSubscription();
            cons.startMessageDelivery();
        }
        for (MessageProducerImpl prod: _producers)
        {
            prod.verifyDestinationForProducer();
            prod.startMessageSender();
        }
    }

    @Override
    public MessageProducer createProducer(Destination dest) throws JMSException
    {
        MessageProducerImpl prod = new MessageProducerImpl(this, dest);
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
        String tag = String.valueOf(_consumerTag.incrementAndGet());
        MessageConsumerImpl cons = new MessageConsumerImpl(tag, this, dest, selector, noLocal, false, _ackMode);
        _consumers.put(tag, cons);

        return cons;
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String selector, boolean noLocal)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void unsubscribe(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub

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
        // TODO Auto-generated method stub
        return null;
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

        String tag = String.valueOf(_consumerTag.incrementAndGet());
        TopicSubscriberImpl cons = new TopicSubscriberImpl(tag, this, topic, selector, noLocal, false, _ackMode);
        _consumers.put(tag, cons);

        return cons;
    }

    @Override
    public QueueReceiver createReceiver(Queue queue) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public QueueReceiver createReceiver(Queue queue, String messageSelector) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public QueueSender createSender(Queue queue) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
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
