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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.apache.qpid.amqp_0_10.jms.ConnectionEvent;
import org.apache.qpid.amqp_0_10.jms.ConnectionEvent.ConnectionEventType;
import org.apache.qpid.amqp_0_10.jms.ConnectionFailedException;
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
import org.apache.qpid.thread.Threading;
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
import org.apache.qpid.transport.TransportFailureException;
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
        UNCONNECTED, STOPPED, STARTED, CLOSING, CLOSED
    }

    private static enum FailoverStatus
    {
        SUCCESSFUL, PRE_FAILOVER_FAILED, RECONNECTION_FAILED, CONN_FAILED_DURING_POST, POST_FAILOVER_FAILED
    }

    private static final AtomicLong CONN_NUMBER_GENERATOR = new AtomicLong(0);

    private final long _connectionNumber;

    private final Object _lock = new Object();

    private ConditionManager _failoverInProgress = new ConditionManager(false);

    private org.apache.qpid.transport.Connection _amqpConnection;

    private final Map<org.apache.qpid.transport.Session, SessionImpl> _sessionMap = new ConcurrentHashMap<org.apache.qpid.transport.Session, SessionImpl>();

    private final List<SessionImpl> _sessions = new CopyOnWriteArrayList<SessionImpl>();

    private final List<TemporaryQueue> _tempQueues = new ArrayList<TemporaryQueue>();

    private volatile State _state = State.UNCONNECTED;

    private final ConnectionMetaDataImpl _metaData = new ConnectionMetaDataImpl();

    private final Collection<CloseTask> _closeTasks = new ArrayList<CloseTask>();

    private final ConnectionConfig _config;

    private final DispatchManager<org.apache.qpid.transport.Session> _dispatchManager;

    private final MessageFactory _messageFactory;

    private final FailoverManager _failoverManager;

    private final List<org.apache.qpid.amqp_0_10.jms.ConnectionListener> _listeners = new ArrayList<org.apache.qpid.amqp_0_10.jms.ConnectionListener>(
            1);

    private String _clientId;

    private ConnectionException _exception;

    private volatile ExceptionListener _exceptionListener;

    private ExecutorService _executor = Executors.newSingleThreadExecutor(Threading.getThreadFactory());

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
                    _amqpConnection = new org.apache.qpid.transport.ClientConnection();
                    _amqpConnection.addConnectionListener(this);
                    _amqpConnection.setSessionFactory(new ClientSessionFactory());
                    _amqpConnection.setConnectionDelegate(new ClientConnectionDelegate(settings, _config.getURL()));
                    _amqpConnection.connect(settings);
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Successfully connected to host : " + settings.getHost() + " port: "
                                + settings.getPort());
                    }
                    notifyConnectionEvent(ConnectionEventType.PROTOCOL_CONNECTION_CREATED);
                    
                    _state = State.STOPPED;
                }
                catch (ProtocolVersionException pe)
                {
                    throw ExceptionHelper.toJMSException("Invalid Protocol Version", pe);
                }
                catch (ConnectionException ce)
                {
                    throw ExceptionHelper.toJMSException("Error connecting to broker", ce);
                }
                catch (TransportException pe)
                {
                    throw new ConnectionFailedException("Unable to connect to broker", pe);
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
                _failoverManager.init(this);
                _failoverManager.connect();
                notifyConnectionEvent(ConnectionEventType.OPENED);
            }
            SessionImpl ssn = new SessionImpl(this, ackMode);
            _sessions.add(ssn);
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
        // if (_clientId != null)
        // {
        // throw new IllegalStateException("client-id has already been set");
        // }
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
                _failoverManager.init(this);
                _failoverManager.connect();
                notifyConnectionEvent(ConnectionEventType.OPENED);
            }

            if (_state == State.STOPPED)
            {
                _state = State.STARTED;

                for (SessionImpl session : _sessions)
                {
                    session.start();
                }

                _dispatchManager.start();
            }

            notifyConnectionEvent(ConnectionEventType.STARTED);

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

                for (SessionImpl session : _sessions)
                {
                    session.stop();
                }

                notifyConnectionEvent(ConnectionEventType.STOPPED);
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

    @Override
    public void addListener(org.apache.qpid.amqp_0_10.jms.ConnectionListener l)
    {
        _listeners.add(l);
    }

    @Override
    public void removeListener(org.apache.qpid.amqp_0_10.jms.ConnectionListener l)
    {
        _listeners.remove(l);
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
        _logger.error("Connection exception received!", exception);
        if (_exception != null)
        {
            _logger.error("Previous connection exception was : " + _exception);
        }
        _exception = exception;

        notifyConnectionEvent(ConnectionEventType.EXCEPTION, exception);
    }

    @Override
    public void closed(org.apache.qpid.transport.Connection connection)
    {
        JMSException closedException = null;
        synchronized (_lock)
        {
            if (_state == State.CLOSED)
            {
                _logger.warn("Connection already marked as closed. No failover is attempted.");
                return;
            }

            if (_exception != null && _exception instanceof TransportFailureException)
            {
                try
                {
                    notifyConnectionEvent(ConnectionEventType.PROTOCOL_CONNECTION_LOST);
                    _failoverInProgress.setValueAndNotify(true);
                    FailoverStatus status = FailoverStatus.SUCCESSFUL;
                    notifyConnectionEvent(ConnectionEventType.PRE_FAILOVER);

                    State prevState = _state;
                    _state = State.UNCONNECTED;
                    try
                    {
                        _logger.warn("Executing pre failover routine");
                        notifyConnectionEvent(ConnectionEventType.PRE_FAILOVER);
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
                                    "All attempts at reconnection failed.", e);
                            status = FailoverStatus.RECONNECTION_FAILED;
                        }
                        catch (JMSException e)
                        {
                            closedException = ExceptionHelper.toJMSException("Reconnection failed due to error", e);
                            status = FailoverStatus.RECONNECTION_FAILED;
                        }
                    }

                    if (status == FailoverStatus.SUCCESSFUL)
                    {
                        try
                        {
                            _logger.warn("Reconnection succesfull, Executing post failover routine");
                            postFailover();
                            notifyConnectionEvent(ConnectionEventType.POST_FAILOVER);
                        }
                        catch (ConnectionFailedException e)
                        {
                            _logger.warn("Broker connection failed before post-failover routine was completed.", e);
                            status = FailoverStatus.CONN_FAILED_DURING_POST;
                        }
                        catch (JMSException e)
                        {
                            _logger.warn("Post failover failed. Closing the connection", e);
                            closedException = ExceptionHelper.toJMSException("Post failover failed.", e);
                            status = FailoverStatus.POST_FAILOVER_FAILED;
                        }
                    }

                    switch (status)
                    {
                    case CONN_FAILED_DURING_POST:
                        return;
                    case PRE_FAILOVER_FAILED:
                    case RECONNECTION_FAILED:
                    case POST_FAILOVER_FAILED:
                        markAsClosing();
                        _failoverInProgress.setValueAndNotify(false);
                        closeImplLogException(status == FailoverStatus.POST_FAILOVER_FAILED);
                        break;

                    default:
                        _failoverInProgress.setValueAndNotify(false);
                        _logger.warn("Failover succesfull. Connection Ready to be used");
                        break;
                    }
                    _lock.notifyAll();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    _logger.error("Uncaught exception during failover", e);
                }
            }
            else
            {
                closeImplLogException(false);
                closedException = ExceptionHelper.toJMSException("Connection closed by broker", _exception);
            }
        }

        // You don't need to access the state inside the lock as once closed
        // the state will never change.
        if (_state == State.CLOSED && _exceptionListener != null)
        {
            final JMSException exp = ExceptionHelper.toJMSException("Connection error", closedException);
            Runnable r = new Runnable()
            {
                public void run()
                {
                    _exceptionListener.onException(exp);
                }
            };
            _executor.execute(r);
        }

        notifyConnectionEvent(ConnectionEventType.CLOSED);
    }

    // -----------------------------------------

    void closeImplLogException(boolean sendClose)
    {
        try
        {
            closeImpl(sendClose);
        }
        catch (JMSException e)
        {
            _logger.warn("Error closing connection", e);
        }
    }

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
                    for (SessionImpl session : _sessions)
                    {
                        session.closeImpl(sendClose, false);
                    }
                    _sessionMap.clear();
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
                _executor.shutdown();
            }
            _lock.notifyAll();
        }
    }

    // ----These methods are only called from closed() method-------
    void preFailover() throws JMSException
    {
        _dispatchManager.markStopped();
        for (SessionImpl ssn : _sessions)
        {
            ssn.preFailover();
        }
        _dispatchManager.clearDispatcherQueues();
    }

    void postFailover() throws JMSException
    {
        for (SessionImpl ssn : _sessions)
        {
            ssn.postFailover();
        }
        _dispatchManager.start();
    }

    void markAsClosing()
    {
        _state = State.CLOSING;
        for (SessionImpl ssn : _sessions)
        {
            ssn.markAsClosing();
        }
    }

    // -----------------------------------------------------------

    org.apache.qpid.transport.Connection getAMQPConnection()
    {
        return _amqpConnection;
    }

    void mapSession(SessionImpl session, org.apache.qpid.transport.Session amqpSession)
    {
        _sessionMap.put(amqpSession, session);
        _dispatchManager.register(session.getAMQPSession());
    }

    void unmapSession(SessionImpl session, org.apache.qpid.transport.Session amqpSession)
    {
        if (amqpSession != null)
        {
            _sessionMap.remove(amqpSession);
            _dispatchManager.unregister(amqpSession);
        }
    }

    void removeSession(SessionImpl session)
    {
        synchronized (_lock)
        {
            _sessions.remove(session);
            org.apache.qpid.transport.Session ssn = session.getAMQPSession();
            unmapSession(session, ssn);
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
        ssn.close();
    }

    void notifyConnectionEvent(ConnectionEventType type)
    {
        notifyConnectionEvent(type, null);
    }

    void notifyConnectionEvent(ConnectionEventType type, Exception exp)
    {
        final ConnectionEvent event = new ConnectionEvent(this, type, exp);
        Runnable r = new Runnable()
        {
            public void run()
            {
                for (org.apache.qpid.amqp_0_10.jms.ConnectionListener l : _listeners)
                {
                    l.connectionEvent(event);
                }
            }
        };
        _executor.execute(r);
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
                if (_sessionMap.get(ssn) == null)
                {
                    _logger.warn("Error! No matching session " + ssn);
                }
                else
                {
                    MessageImpl msg = (MessageImpl) _messageFactory.createMessage(_sessionMap.get(ssn), xfr);
                    _dispatchManager.dispatch(msg);
                }
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
            SessionImpl ssn = _sessionMap.get(session);
            ssn.setException(exception);            
        }
    }

    @Override
    public void closed(org.apache.qpid.transport.Session session)
    {
        if (_state != State.CLOSED)
        {
            SessionImpl ssn = _sessionMap.get(session);

            if (ssn != null && ssn.getException() != null)
            {
                removeSession(ssn);
                try
                {
                    ssn.closeImpl(false, false);
                }
                catch (JMSException e)
                {
                    _logger.warn(e, "Error closing session");
                }
                if (_config.isNotifySessionExceptions())
                {
                    _exceptionListener.onException(ExceptionHelper.toJMSException("Session exception", ssn.getException()));
                }
            }
        }
    }

    @Override
    public void commandCompleted(ClientSession session, int commandId)
    {
        if (_state != State.CLOSED)
        {
            SessionImpl ssn = _sessionMap.get(session);
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
            if (_state == State.CLOSED || _state == State.CLOSING)
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