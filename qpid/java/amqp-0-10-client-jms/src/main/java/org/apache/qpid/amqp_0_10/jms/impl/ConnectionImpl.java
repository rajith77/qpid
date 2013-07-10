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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.jms.Connection;
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

import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.client.JmsNotImplementedException;
import org.apache.qpid.client.message.MessageFactory;
import org.apache.qpid.client.transport.ClientConnectionDelegate;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.transport.ConnectionException;
import org.apache.qpid.transport.ConnectionListener;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.ProtocolVersionException;
import org.apache.qpid.transport.SessionDetachCode;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.SessionListener;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.ExceptionHelper;

public class ConnectionImpl implements Connection, TopicConnection, QueueConnection, ConnectionListener,
        SessionListener
{
    private static final Logger _logger = Logger.get(ConnectionImpl.class);

    private static enum State
    {
        UNCONNECTED, STOPPED, STARTED, CLOSED
    }

    private final Lock _conditionLock = new ReentrantLock();

    private final Object _lock = new Object();

    private AtomicBoolean _failoverInProgress = new AtomicBoolean(false);

    private final Condition _failoverComplete = _conditionLock.newCondition();

    private final Condition _started = _conditionLock.newCondition();

    private org.apache.qpid.transport.Connection _amqpConnection;

    private final List<SessionImpl> _sessions = new ArrayList<SessionImpl>();

    private volatile State _state = State.UNCONNECTED;

    private final AMQConnectionURL _url;

    private final ConnectionMetaDataImpl _metaData = new ConnectionMetaDataImpl();

    private final ConnectionConfig _config;

    private String _clientId;

    private volatile ExceptionListener _exceptionListener;

    protected ConnectionImpl(AMQConnectionURL url) throws JMSException
    {
        _url = url;
        _amqpConnection = new org.apache.qpid.transport.Connection();
        _amqpConnection.addConnectionListener(this);
        _config = new ConnectionConfig(url);
    }

    private void connect(ConnectionSettings conSettings) throws JMSException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Attempting connection to host: " + conSettings.getHost() + " port: " + conSettings.getPort()
                    + " vhost: " + _url.getVirtualHost() + " username: " + conSettings.getUsername());
        }

        synchronized (_lock)
        {
            if (_state == State.UNCONNECTED)
            {
                _state = State.STOPPED;
                try
                {

                    _amqpConnection.setConnectionDelegate(new ClientConnectionDelegate(conSettings, _url));
                    _amqpConnection.connect(conSettings);
                }
                catch (ProtocolVersionException pe)
                {
                    throw ExceptionHelper.toJMSException("Invalid Protocol Version", pe);
                }
                catch (ConnectionException ce)
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
        synchronized (_lock)
        {
            if (_state != State.CLOSED)
            {
                stop();
                for (SessionImpl session : _sessions)
                {
                    session.close();
                }

                if (_amqpConnection != null && _state != State.UNCONNECTED)
                {
                    _amqpConnection.close();
                }
                _state = State.CLOSED;
            }

            _lock.notifyAll();
        }
    }

    @Override
    public Session createSession(boolean transacted, int acknowledgeMode) throws JMSException
    {
        int ackMode = transacted ? Session.SESSION_TRANSACTED : acknowledgeMode;
        return createSession(ackMode);
    }

    @Override
    public QueueSession createQueueSession(boolean transacted, int acknowledgeMode) throws JMSException
    {
        int ackMode = transacted ? Session.SESSION_TRANSACTED : acknowledgeMode;
        return createSession(ackMode);
    }

    @Override
    public TopicSession createTopicSession(boolean transacted, int acknowledgeMode) throws JMSException
    {
        int ackMode = transacted ? Session.SESSION_TRANSACTED : acknowledgeMode;
        return createSession(ackMode);
    }

    private SessionImpl createSession(int ackMode) throws JMSException
    {
        checkClosed();
        if (_state == State.UNCONNECTED)
        {
            ConnectionSettings conSettings = retrieveConnectionSettings(_url.getBrokerDetails(0));
            connect(conSettings);
        }
        SessionImpl ssn = new SessionImpl(this, ackMode);
        _sessions.add(ssn);
        return ssn;
    }

    @Override
    public String getClientID() throws JMSException
    {
        return _clientId;
    }

    @Override
    public ExceptionListener getExceptionListener() throws JMSException
    {
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
        if (_clientId != null)
        {
            throw new IllegalStateException("client-id has already been set");
        }
        verifyClientID();
        _clientId = id;
    }

    @Override
    public void setExceptionListener(ExceptionListener listener) throws JMSException
    {
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
                ConnectionSettings conSettings = retrieveConnectionSettings(_url.getBrokerDetails(0));
                connect(conSettings);
                _state = State.STARTED;
            }

            if (_state == State.STOPPED)
            {
                _state = State.STARTED;

                for (SessionImpl session : _sessions)
                {
                    session.start();
                }
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
                for (SessionImpl session : _sessions)
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

    // ----------------------------------------
    // ConnectionListener
    // -----------------------------------------
    @Override
    public void opened(org.apache.qpid.transport.Connection connection)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void exception(org.apache.qpid.transport.Connection connection, ConnectionException exception)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void closed(org.apache.qpid.transport.Connection connection)
    {
        // TODO Auto-generated method stub
    }

    protected org.apache.qpid.transport.Connection getAMQPConnection()
    {
        return _amqpConnection;
    }

    protected void removeSession(SessionImpl ssn)
    {
        _sessions.remove(ssn);
    }

    // ----------------------------------------
    // SessionListener
    // -----------------------------------------
    @Override
    public void opened(org.apache.qpid.transport.Session session)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void resumed(org.apache.qpid.transport.Session session)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void message(org.apache.qpid.transport.Session ssn, MessageTransfer xfr)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void exception(org.apache.qpid.transport.Session session, SessionException exception)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void closed(org.apache.qpid.transport.Session session)
    {
        // TODO Auto-generated method stub
    }

    // --------------------------------------
    boolean isStarted()
    {
        return _state == State.STARTED;
    }

    boolean isFailoverInProgress()
    {
        return _failoverInProgress.get();
    }

    long waitForFailoverToComplete(long timeout) throws InterruptedException
    {
        _conditionLock.lock();
        try
        {
            synchronized (_failoverInProgress)
            {
                if (_failoverInProgress.get())
                {
                    long remaining = _failoverComplete.awaitNanos(TimeUnit.MILLISECONDS.toNanos(timeout));
                    return TimeUnit.NANOSECONDS.toMillis(remaining);
                }
                else
                {
                    return timeout;
                }
            }
        }
        finally
        {
            _conditionLock.unlock();
        }
    }

    ConnectionConfig getConfig()
    {
        return _config;
    }

    private ConnectionSettings retrieveConnectionSettings(BrokerDetails brokerDetail)
    {
        ConnectionSettings conSettings = brokerDetail.buildConnectionSettings();

        conSettings.setVhost(_url.getVirtualHost());
        conSettings.setUsername(_url.getUsername());
        conSettings.setPassword(_url.getPassword());

        // Pass client name from connection URL
        Map<String, Object> clientProps = new HashMap<String, Object>();
        clientProps.put(ConnectionStartProperties.CLIENT_ID_0_10, _clientId);
        conSettings.setClientProperties(clientProps);

        conSettings.setHeartbeatInterval(getHeartbeatInterval(brokerDetail));

        // Check connection-level ssl override setting
        String connectionSslOption = _url.getOption(ConnectionURL.OPTIONS_SSL);
        if (connectionSslOption != null)
        {
            boolean connUseSsl = Boolean.parseBoolean(connectionSslOption);
            boolean brokerlistUseSsl = conSettings.isUseSSL();

            if (connUseSsl != brokerlistUseSsl)
            {
                conSettings.setUseSSL(connUseSsl);

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Applied connection ssl option override, setting UseSsl to: " + connUseSsl);
                }
            }
        }

        return conSettings;
    }

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

    // The idle_timeout prop is in milisecs while
    // the new heartbeat prop is in secs
    private int getHeartbeatInterval(BrokerDetails brokerDetail)
    {
        int heartbeat = 0;
        if (brokerDetail.getProperty(BrokerDetails.OPTIONS_IDLE_TIMEOUT) != null)
        {
            _logger.warn("Broker property idle_timeout=<mili_secs> is deprecated, please use heartbeat=<secs>");
            heartbeat = Integer.parseInt(brokerDetail.getProperty(BrokerDetails.OPTIONS_IDLE_TIMEOUT)) / 1000;
        }
        else if (brokerDetail.getProperty(BrokerDetails.OPTIONS_HEARTBEAT) != null)
        {
            heartbeat = Integer.parseInt(brokerDetail.getProperty(BrokerDetails.OPTIONS_HEARTBEAT));
        }
        else if (Integer.getInteger(ClientProperties.IDLE_TIMEOUT_PROP_NAME) != null)
        {
            heartbeat = Integer.getInteger(ClientProperties.IDLE_TIMEOUT_PROP_NAME) / 1000;
            _logger.warn("JVM arg -Didle_timeout=<mili_secs> is deprecated, please use -Dqpid.heartbeat=<secs>");
        }
        else if (Integer.getInteger(ClientProperties.HEARTBEAT) != null)
        {
            heartbeat = Integer.getInteger(ClientProperties.HEARTBEAT, ClientProperties.HEARTBEAT_DEFAULT);
        }
        else
        {
            heartbeat = Integer.getInteger("amqj.heartbeat.delay", ClientProperties.HEARTBEAT_DEFAULT);
        }
        return heartbeat;
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