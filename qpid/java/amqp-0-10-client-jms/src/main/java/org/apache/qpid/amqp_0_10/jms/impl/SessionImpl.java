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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
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

import org.apache.qpid.client.JmsNotImplementedException;
import org.apache.qpid.client.message.MessageFactory;
import org.apache.qpid.transport.ConnectionException;
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
                    _logger.warn(e,"Error flushing pending acknowledgements");
                }
            }
        }
    }

    private org.apache.qpid.transport.Session _amqpSession;

    private final ConnectionImpl _conn;

    private final AcknowledgeMode _ackMode;

    private long _maxAckDelay = Long.getLong("qpid.session.max_ack_delay", 5*60000);

    private TimerTask _flushTask = null;

    private final List<MessageProducerImpl> _producers = new ArrayList<MessageProducerImpl>(2);

    private final Map<String, MessageConsumerImpl> _consumers = new HashMap<String, MessageConsumerImpl>(2);
    
    private final AtomicBoolean _closed = new AtomicBoolean(false);
    
    private final MessageFactory _messageFactory;
    
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

        if (_maxAckDelay > 0)
        {
            _flushTask = new Flusher(this);
            timer.schedule(_flushTask, new Date(), _maxAckDelay);
        }
    }

    @Override
    public void close() throws JMSException
    {
        if (!_closed.get())
        {
            _closed.set(true);
            cancelTimerTask();
            for (MessageProducerImpl prod: _producers)
            {
                prod.close();
            }
            
            for (MessageConsumerImpl cons: _consumers.values())
            {
                cons.close();
            }
            getAMQPSession().close();
            _conn.removeSession(this);
        }
    }

    // Called when the peer closes the session
    protected void closed()
    {
        if (!_closed.get())
        {
            _closed.set(true);
            cancelTimerTask();
            for (MessageProducerImpl prod: _producers)
            {
                prod.closed();
            }
            
            for (MessageConsumerImpl cons: _consumers.values())
            {
                cons.closed();
            }
            _conn.removeSession(this);
        }
    }

    @Override
    public void commit() throws JMSException
    {
        checkClosed();
        checkTransactional();

        for (MessageConsumerImpl cons: _consumers.values())
        {
            cons.commit();
        }
        
        try
        {
            _amqpSession.setAutoSync(true);
            _amqpSession.txCommit();
            _amqpSession.setAutoSync(false);
        }
        catch (SessionException se)
        {
            closed();
            throw ExceptionHelper.toJMSException("Commit failed due to error", se);
        }
    }

    @Override
    public void rollback() throws JMSException
    {
        checkClosed();
        checkTransactional();

        for (MessageConsumerImpl cons: _consumers.values())
        {
            cons.rollback();
        }
        
        try
        {
            _amqpSession.setAutoSync(true);
            _amqpSession.txRollback();
            _amqpSession.setAutoSync(false);
        }
        catch (SessionException se)
        {
            closed();
            throw ExceptionHelper.toJMSException("Rollback failed due to error", se);
        }
    }

    @Override
    public void recover() throws JMSException
    {
        checkClosed();
        checkNotTransactional();
    }

    public void start()
    {
        // TODO Auto-generated method stub

    }

    public void stop()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public MessageProducer createProducer(Destination arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public MessageConsumer createConsumer(Destination arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public MessageConsumer createConsumer(Destination arg0, String arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public MessageConsumer createConsumer(Destination arg0, String arg1, boolean arg2) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic arg0, String arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic arg0, String arg1, String arg2, boolean arg3)
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TextMessage createTextMessage() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public TextMessage createTextMessage(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public MapMessage createMapMessage() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Queue createQueue(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
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
    public Topic createTopic(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
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
        if(_closed.get())
        {
            throw new IllegalStateException("Session is closed");
        }
    }

    protected void removeProducer(MessageProducerImpl prod)
    {
        _producers.remove(prod);
    }

    protected void removeConsumer(MessageConsumerImpl cons)
    {
        _consumers.remove(cons);
    }
    
    protected org.apache.qpid.transport.Session getAMQPSession()
    {
        return _amqpSession;
    }
    
    private void flushPendingAcknowledgements() throws JMSException
    {
        for(MessageConsumerImpl consumer : _consumers.values())
        {
            consumer.sendMessageAccept(true);
        }
    }

    private void checkTransactional() throws JMSException
    {
        if(!getTransacted())
        {
            throw new IllegalStateException("Session must be transacted in order to perform this operation");
        }
    }

    private void checkNotTransactional() throws JMSException
    {
        if(getTransacted())
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
    
    //--------------- Unsupported Methods -------------
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

    void acknowledgeMesages()
    {
        
    }

}
