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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.InvalidClientIDException;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;

import org.apache.qpid.amqp_0_10.jms.Connection;
import org.apache.qpid.amqp_0_10.jms.FailoverManager;
import org.apache.qpid.amqp_0_10.jms.FailoverUnsuccessfulException;
import org.apache.qpid.amqp_0_10.jms.MessageFactory;
import org.apache.qpid.amqp_0_10.jms.impl.dispatch.DispatchManager;
import org.apache.qpid.amqp_0_10.jms.impl.dispatch.DispatchManagerImpl;
import org.apache.qpid.amqp_0_10.jms.impl.failover.FailoverManagerSupport;
import org.apache.qpid.amqp_0_10.jms.impl.message.MessageFactorySupport;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.JmsNotImplementedException;
import org.apache.qpid.client.transport.ClientConnectionDelegate;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.transport.ClientSession;
import org.apache.qpid.transport.ClientSessionFactory;
import org.apache.qpid.transport.ClientSessionListener;
import org.apache.qpid.transport.ConnectionException;
import org.apache.qpid.transport.ConnectionListener;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.ProtocolVersionException;
import org.apache.qpid.transport.SessionDetachCode;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.util.ConditionManager;
import org.apache.qpid.util.ConditionManagerTimeoutException;
import org.apache.qpid.util.ExceptionHelper;

public class ConnectionImpl implements Connection, TopicConnection, QueueConnection, ConnectionListener,
        ClientSessionListener
{
    private static final Logger _logger = Logger.get(ConnectionImpl.class);

    private static enum State
    {
        UNCONNECTED, STOPPED, STARTED, CLOSED
    }

    private static enum FailoverStatus
    {
        SUCCESSFUL, PRE_FAILOVER_FAILED, RECONNECTION_FAILED, POST_FAILOVER_FAILED
    }

    private static final AtomicLong CONN_NUMBER_GENERATOR = new AtomicLong(0);

    private final long _connectionNumber;

    private final Object _lock = new Object();

    private ConditionManager _failoverInProgress = new ConditionManager(false);

    private org.apache.qpid.transport.Connection _amqpConnection;

    private final Map<org.apache.qpid.transport.Session, SessionImpl> _sessions = new ConcurrentHashMap<org.apache.qpid.transport.Session, SessionImpl>();

    private final List<TemporaryQueue> _tempQueues = new ArrayList<TemporaryQueue>();

    private volatile State _state = State.UNCONNECTED;

    private final ConnectionMetaDataImpl _metaData = new ConnectionMetaDataImpl();

    private final Collection<CloseTask> _closeTasks = new ArrayList<CloseTask>();

    private final ConnectionConfig _config;

    private final DispatchManager<org.apache.qpid.transport.Session> _dispatchManager;

    private final MessageFactory _messageFactory;

    private final FailoverManager _failoverManager;

    private String _clientId;

    private volatile ExceptionListener _exceptionListener;

    public ConnectionImpl(String url) throws JMSException, URLSyntaxException
    {
        this(new AMQConnectionURL(url));
    }

    protected ConnectionImpl(AMQConnectionURL url) throws JMSException
    {
        _config = new ConnectionConfig(this, url);
        _clientId = url.getClientName();
        _dispatchManager = new DispatchManagerImpl(this);
        _connectionNumber = CONN_NUMBER_GENERATOR.incrementAndGet();
        _messageFactory = MessageFactorySupport.getMessageFactory(null);
        _failoverManager = FailoverManagerSupport.getFailoverManager(null);
        _failoverManager.init(this);
    }

    public void connect(ConnectionSettings settings) throws JMSException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Attempting connection to host: " + settings.getHost() + " port: " + settings.getPort()
                    + " vhost: " + settings.getVhost() + " username: " + settings.getUsername());
        }

        synchronized (_lock)
        {
            if (_state == State.UNCONNECTED)
            {
                try
                {
                    _amqpConnection = new org.apache.qpid.transport.Connection();
                    _amqpConnection.addConnectionListener(this);
                    _amqpConnection.setSessionFactory(new ClientSessionFactory());
                    _amqpConnection.setConnectionDelegate(new ClientConnectionDelegate(settings, _config.getURL()));
                    _amqpConnection.connect(settings);
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Successfully connected to host : " + settings.getHost() + " port: "
                                + settings.getPort());
                    }
                    _state = State.STOPPED;
                }
                catch (ProtocolVersionException pe)
                {
                    throw ExceptionHelper.toJMSException("Invalid Protocol Version", pe);
                }
                catch (TransportException ce)
                {
                    String msg = "Cannot connect to broker: " + ce.getMessage();
                    throw ExceptionHelper.toJMSException(msg, ce);
                }
            }
        }

        try
        {
            verifyClientID();
        }
        catch (InvalidClientIDException e)
        {
            _amqpConnection.close();
            throw e;
        }
    }

    @Override
    public void close() throws JMSException
    {
        closeImpl(true);
    }

    @Override
    public Session createSession(boolean transacted, int acknowledgeMode) throws JMSException
    {
        return createSessionImpl(transacted, acknowledgeMode);
    }

    @Override
    public QueueSession createQueueSession(boolean transacted, int acknowledgeMode) throws JMSException
    {
        return createSessionImpl(transacted, acknowledgeMode);
    }

    @Override
    public TopicSession createTopicSession(boolean transacted, int acknowledgeMode) throws JMSException
    {

        return createSessionImpl(transacted, acknowledgeMode);
    }

    private SessionImpl createSessionImpl(boolean transacted, int acknowledgeMode) throws JMSException
    {
        checkClosed();
        synchronized (_lock)
        {
            int ackMode = transacted ? Session.SESSION_TRANSACTED : acknowledgeMode;
            if (_state == State.UNCONNECTED)
            {
                _failoverManager.connect();
            }
            SessionImpl ssn = new SessionImpl(this, ackMode);

            if (_state == State.STARTED)
            {
                ssn.start();
            }

            return ssn;
        }
    }

    @Override
    public String getClientID() throws JMSException
    {
        checkClosed();
        return _clientId;
    }

    @Override
    public ExceptionListener getExceptionListener() throws JMSException
    {
        checkClosed();
        return _exceptionListener;
    }

    @Override
    public ConnectionMetaData getMetaData() throws JMSException
    {
        return _metaData;
    }

    @Override
    public void setClientID(String id) throws JMSException
    {
        checkNotConnected("Cannot set client-id to \"" + id + "\"; client-id must be set before the connection is used");
        //if (_clientId != null)
        //{
        //    throw new IllegalStateException("client-id has already been set");
        //}
        verifyClientID();
        _clientId = id;
    }

    @Override
    public void setExceptionListener(ExceptionListener listener) throws JMSException
    {
        checkClosed();
        _exceptionListener = listener;
    }

    @Override
    public void start() throws JMSException
    {
        synchronized (_lock)
        {
            checkClosed();

            if (_state == State.UNCONNECTED)
            {
                _failoverManager.connect();
            }

            if (_state == State.STOPPED)
            {
                _state = State.STARTED;

                for (SessionImpl session : _sessions.values())
                {
                    session.start();
                }

                _dispatchManager.start();
            }

            _lock.notifyAll();
        }
    }

    @Override
    public void stop() throws JMSException
    {
        synchronized (_lock)
        {
            checkClosed();
            if (_state == State.STARTED)
            {
                _state = State.STOPPED;
                _dispatchManager.stop();

                for (SessionImpl session : _sessions.values())
                {
                    session.stop();
                }
            }
            else if (_state == State.UNCONNECTED)
            {
                _state = State.STOPPED;
            }

            _lock.notifyAll();
        }
    }

    public long getConnectionId()
    {
        return _connectionNumber;
    }

    public MessageFactory getMessageFactory()
    {
        return _messageFactory;
    }

    // ----------------------------------------
    // ConnectionListener
    // -----------------------------------------
    @Override
    public void opened(org.apache.qpid.transport.Connection connection)
    {
        // Not used.
    }

    @Override
    public void exception(org.apache.qpid.transport.Connection connection, ConnectionException exception)
    {
        _logger.warn("Connection exception received!", exception);
    }

    @Override
    public void closed(org.apache.qpid.transport.Connection connection)
    {
        _failoverInProgress.waitUntilFalse();
        _failoverInProgress.setValueAndNotify(true);
        FailoverStatus status = FailoverStatus.SUCCESSFUL;
        JMSException closedException = null;
        try
        {
            synchronized (_lock)
            {               
                State prevState = _state;
                _state = State.UNCONNECTED;
                try
                {
                    _logger.warn("Executing pre failover routine");
                    preFailover();
                }
                catch (JMSException e)
                {
                    _logger.warn("Pre failover failed. Aborting failover", e);
                    closedException = ExceptionHelper.toJMSException("Pre failover failed. Aborting failover", e);
                    status = FailoverStatus.PRE_FAILOVER_FAILED;
                }

                if (status == FailoverStatus.SUCCESSFUL)
                {
                    try
                    {
                        _failoverManager.connect();
                        _state = prevState; // either START or STOPPED
                    }
                    catch (FailoverUnsuccessfulException e)
                    {
                        _logger.warn("All attempts at reconnection failed. Aborting failover", e);
                        closedException = ExceptionHelper.toJMSException(
                                "All attempts at reconnection failed. Aborting failover", e);
                        status = FailoverStatus.RECONNECTION_FAILED;
                    }
                }

                if (status == FailoverStatus.SUCCESSFUL)
                {
                    try
                    {
                        _logger.warn("Reconnection succesfull, Executing post failover routine");
                        postFailover();
                    }
                    catch (JMSException e)
                    {
                        _logger.warn("Post failover failed. Closing the connection", e);
                        closedException = ExceptionHelper.toJMSException(
                                "Post failover failed. Closing the connection", e);
                        status = FailoverStatus.POST_FAILOVER_FAILED;
                    }
                }

                switch (status)
                {
                case PRE_FAILOVER_FAILED:
                case RECONNECTION_FAILED:
                case POST_FAILOVER_FAILED:

                    try
                    {
                        markAsClosing();
                        _failoverInProgress.setValueAndNotify(false);
                        closeImpl(status == FailoverStatus.POST_FAILOVER_FAILED);
                    }
                    catch (JMSException e)
                    {
                        _logger.warn("Connection close failed", e);
                    }
                    break;

                default:
                    _failoverInProgress.setValueAndNotify(false);
                    _logger.warn("Failover succesfull. Connection Ready to be used");
                    break;
                }
                _lock.notifyAll();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            _failoverInProgress.setValueAndNotify(false);
        }

        // You don't need to access the state inside the lock as once closed
        // the state will never change.
        if (_state == State.CLOSED && _exceptionListener != null)
        {
            _exceptionListener.onException(closedException);
        }
    }

    // -----------------------------------------

    void closeImpl(boolean sendClose) throws JMSException
    {
        synchronized (_lock)
        {
            if (_state != State.CLOSED)
            {
                _state = State.CLOSED;
                _dispatchManager.shutdown();

                try
                {
                    for (SessionImpl session : _sessions.values())
                    {
                        session.closeImpl(sendClose, false);
                    }
                    _sessions.clear();

                    if (sendClose)
                    {
                        removeTempDestinations();
                    }
                    else
                    {
                        _tempQueues.clear();
                    }

                    for (CloseTask task : _closeTasks)
                    {
                        task.onClose();
                    }
                }
                finally
                {
                    if (sendClose && _amqpConnection != null && _state != State.UNCONNECTED)
                    {
                        _amqpConnection.close();
                    }
                }

            }
            _lock.notifyAll();
        }
    }

    // ----------------These methods are only called from closed()
    // method----------------------
    void preFailover() throws JMSException
    {
        _dispatchManager.markStop();
        for (SessionImpl ssn : _sessions.values())
        {
            ssn.preFailover();
        }
        _dispatchManager.clearDispatcherQueues();
    }

    void postFailover() throws JMSException
    {
        for (SessionImpl ssn : _sessions.values())
        {
            ssn.postFailover();
        }
        _dispatchManager.start();
    }

    void markAsClosing()
    {
        for (SessionImpl ssn : _sessions.values())
        {
            ssn.markAsClosing();
        }
    }

    // -----------------------------------------------------------

    org.apache.qpid.transport.Connection getAMQPConnection()
    {
        return _amqpConnection;
    }

    void addSession(SessionImpl session)
    {
        synchronized (_lock)
        {
            _sessions.put(session.getAMQPSession(), session);
            _dispatchManager.register(session.getAMQPSession());
        }
    }

    void removeSession(SessionImpl session, boolean waitUntilDispatcherStopped)
    {
        synchronized (_lock)
        {
            org.apache.qpid.transport.Session ssn = session.getAMQPSession();
            if (ssn != null)
            {
                //ssn.setSessionListener(null);
                _sessions.remove(ssn);
                _dispatchManager.unregister(ssn, waitUntilDispatcherStopped);
            }
        }
    }

    boolean isStarted()
    {
        return _state == State.STARTED;
    }

    boolean isFailoverInProgress()
    {
        return _failoverInProgress.getCurrentValue();
    }

    void waitForFailoverToComplete()
    {
        _failoverInProgress.waitUntilFalse();
    }

    /**
     * @param timeout
     *            : If timeout == 0, then wait until true.
     * @return
     */
    long waitForFailoverToComplete(long timeout) throws ConditionManagerTimeoutException
    {
        return _failoverInProgress.waitUntilFalse(timeout);
    }

    void stopDispatcherForSession(SessionImpl ssn)
    {
        synchronized (_lock)
        {
            _dispatchManager.stopDispatcher(ssn.getAMQPSession());
        }
    }

    void startDispatcherForSession(SessionImpl ssn)
    {
        synchronized (_lock)
        {
            _dispatchManager.startDispatcher(ssn.getAMQPSession());
        }
    }

    void requeueMessage(SessionImpl ssn, MessageImpl msg)
    {
        synchronized (_lock)
        {
            _dispatchManager.requeue(ssn.getAMQPSession(), msg);
        }
    }

    void requeueMessages(SessionImpl ssn, List<MessageImpl> list)
    {
        synchronized (_lock)
        {
            for (MessageImpl msg : list)
            {
                _dispatchManager.requeue(ssn.getAMQPSession(), msg);
            }
        }
    }

    void sortDispatchQueue(SessionImpl ssn)
    {
        synchronized (_lock)
        {
            _dispatchManager.sortDispatchQueue(ssn.getAMQPSession());
        }
    }

    public ConnectionConfig getConfig()
    {
        return _config;
    }

    static interface CloseTask
    {
        public void onClose() throws JMSException;
    }

    void addOnCloseTask(CloseTask task)
    {
        synchronized (_lock)
        {
            _closeTasks.add(task);
        }
    }

    void removeOnCloseTask(CloseTask task)
    {
        synchronized (_lock)
        {
            _closeTasks.remove(task);
        }
    }

    void addTempQueue(TemporaryQueue dest)
    {
        _tempQueues.add(dest);
    }

    void removeTempQueue(TemporaryQueue dest)
    {
        _tempQueues.remove(dest);
    }

    void removeTempQueues(Collection<TemporaryQueue> dests)
    {
        _tempQueues.removeAll(dests);
    }

    // Safety net for temp destinations created off sessions that got closed due
    // to errors.
    void removeTempDestinations()
    {
        if (_tempQueues.size() == 0)
        {
            _logger.info("No temporary destinations to delete");
            return;
        }

        org.apache.qpid.transport.Session ssn;
        try
        {
            ssn = _amqpConnection.createSession();
        }
        catch (Exception e)
        {
            _logger.warn(e, "Error creating protocol session for deleting temp queues");
            return;
        }

        for (Iterator<TemporaryQueue> it = _tempQueues.iterator(); it.hasNext();)
        {
            TemporaryQueue dest = null;
            try
            {
                dest = it.next();
                ssn.queueDelete(dest.getQueueName());
                it.remove();
                dest = null;
            }
            catch (Exception e)
            {
                _logger.warn(e, "Error deleting temp queue " + dest == null ? "" : ":" + dest.getQueueName());
            }
        }
    }

    // ----------------------------------------
    // SessionListener
    // -----------------------------------------
    @Override
    public void opened(org.apache.qpid.transport.Session session)
    {
        // Not used.
    }

    @Override
    public void resumed(org.apache.qpid.transport.Session session)
    {
        // Not used.
    }

    @Override
    public void message(org.apache.qpid.transport.Session ssn, MessageTransfer xfr)
    {
        if (_state != State.CLOSED)
        {
            try
            {
                if (_sessions.get(ssn) == null)
                {                    
                    _logger.warn("Error! No matching session " + ssn);
                }
                
                MessageImpl msg = (MessageImpl) _messageFactory.createMessage(_sessions.get(ssn), xfr);
                _dispatchManager.dispatch(msg);
            }
            catch (Exception e)
            {
                _logger.warn(e, "Error dispatching message to session");
            }
        }
    }

    @Override
    public void exception(org.apache.qpid.transport.Session session, SessionException exception)
    {
        if (_state != State.CLOSED)
        {
            SessionImpl ssn = _sessions.get(session);
            ssn.setException(exception);
            // TODO notify session exception via ExceptionListener
        }
    }

    @Override
    public void closed(org.apache.qpid.transport.Session session)
    {
        if (_state != State.CLOSED)
        {
            SessionImpl ssn = _sessions.get(session);

            if (ssn != null && ssn.getException() != null)
            {
                removeSession(ssn, true);
                try
                {
                    ssn.closeImpl(false, false);
                }
                catch (JMSException e)
                {
                    _logger.warn(e, "Error closing session");
                }
            }
        }
    }

    @Override
    public void commandCompleted(ClientSession session, int commandId)
    {
        if (_state != State.CLOSED)
        {
            SessionImpl ssn = _sessions.get(session);
            if (ssn != null)
            {
                ssn.commandCompleted(commandId);
            }
        }
    }

    // --------------------------------------

    private void verifyClientID() throws InvalidClientIDException
    {
        if (Boolean.getBoolean(ClientProperties.QPID_VERIFY_CLIENT_ID))
        {
            org.apache.qpid.transport.Session ssn_0_10 = _amqpConnection.createSession(_clientId);
            try
            {
                ssn_0_10.awaitOpen();
            }
            catch (SessionException se)
            {
                // if due to non unique client id for user return false,
                // otherwise wrap and re-throw.
                if (ssn_0_10.getDetachCode() != null && ssn_0_10.getDetachCode() == SessionDetachCode.SESSION_BUSY)
                {
                    throw new InvalidClientIDException("ClientID must be unique");
                }
                else
                {
                    String msg = "Unexpected SessionException thrown while awaiting session opening";
                    InvalidClientIDException ex = new InvalidClientIDException(msg,
                            SessionDetachCode.UNKNOWN_IDS.name());
                    ex.initCause(se);
                    ex.setLinkedException(se);
                    throw ex;
                }
            }
        }
    }

    private void checkNotConnected(String msg) throws IllegalStateException
    {
        synchronized (_lock)
        {
            if (_state != State.UNCONNECTED)
            {
                throw new IllegalStateException(msg);
            }
        }
    }

    private void checkClosed() throws IllegalStateException
    {
        synchronized (_lock)
        {
            if (_state == State.CLOSED)
            {
                throw new IllegalStateException("Connection is " + _state);
            }
        }
    }

    // ----------------------------------------
    // Unimplemented methods
    // ----------------------------------------
    public ConnectionConsumer createConnectionConsumer(Destination destination, String messageSelector,
            ServerSessionPool sessionPool, int maxMessages) throws JMSException
    {
        checkClosed();
        throw new JmsNotImplementedException();
    }

    public ConnectionConsumer createConnectionConsumer(Queue queue, String messageSelector,
            ServerSessionPool sessionPool, int maxMessages) throws JMSException
    {
        checkClosed();
        throw new JmsNotImplementedException();
    }

    public ConnectionConsumer createConnectionConsumer(Topic topic, String messageSelector,
            ServerSessionPool sessionPool, int maxMessages) throws JMSException
    {
        checkClosed();
        throw new JmsNotImplementedException();
    }

    public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subscriptionName,
            String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException
    {
        checkClosed();
        throw new JmsNotImplementedException();
    }
}