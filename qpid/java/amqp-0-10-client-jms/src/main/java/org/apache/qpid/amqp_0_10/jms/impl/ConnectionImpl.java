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
import org.apache.qpid.client.transport.ClientConnectionDelegate;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.transport.ConnectionCloseCode;
import org.apache.qpid.transport.ConnectionException;
import org.apache.qpid.transport.ConnectionListener;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.ProtocolVersionException;
import org.apache.qpid.transport.SessionDetachCode;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.ExceptionHelper;

public class ConnectionImpl implements Connection, TopicConnection, QueueConnection, ConnectionListener
{
    private static final Logger _logger = Logger.get(ConnectionImpl.class);

    private static enum State
    {
        UNCONNECTED, STOPPED, STARTED, CLOSED
    }

    private final Object _lock = new Object();

    private org.apache.qpid.transport.Connection _qpidConnection;

    private final List<SessionImpl> _sessions = new ArrayList<SessionImpl>();

    private volatile State _state = State.UNCONNECTED;

    private AMQConnectionURL _url;

    private String _clientId;

    private ConnectionMetaDataImpl _metaData = new ConnectionMetaDataImpl();

    private volatile ExceptionListener _exceptionListener;

    protected ConnectionImpl(AMQConnectionURL url) throws JMSException
    {
        _url = url;
        _qpidConnection = new org.apache.qpid.transport.Connection();
        _qpidConnection.addConnectionListener(this);
        ConnectionSettings conSettings = retrieveConnectionSettings(url
                .getBrokerDetails(0));
        connect(conSettings);
        try
        {
            verifyClientID();
        }
        catch (InvalidClientIDException e)
        {
            _qpidConnection.close();
            throw e;
        }
    }

    private void connect(ConnectionSettings conSettings) throws JMSException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Attempting connection to host: "
                    + conSettings.getHost() + " port: " + conSettings.getPort()
                    + " vhost: " + _url.getVirtualHost() + " username: "
                    + conSettings.getUsername());
        }

        synchronized (_lock)
        {
            if (_state == State.UNCONNECTED)
            {
                _state = State.STOPPED;
                try
                {

                    _qpidConnection
                            .setConnectionDelegate(new ClientConnectionDelegate(
                                    conSettings, _url));
                    _qpidConnection.connect(conSettings);
                }
                catch (ProtocolVersionException pe)
                {
                    throw ExceptionHelper.toJMSException(
                            "Invalid Protocol Version", pe);
                }
                catch (ConnectionException ce)
                {
                    String code = ConnectionCloseCode.NORMAL.name();
                    if (ce.getClose() != null
                            && ce.getClose().getReplyCode() != null)
                    {
                        code = ce.getClose().getReplyCode().name();
                    }
                    String msg = "Cannot connect to broker: " + ce.getMessage();
                    throw ExceptionHelper.toJMSException(msg, code, ce);
                }
            }
        }
    }

    @Override
    public void close() throws JMSException
    {

    }

    @Override
    public ConnectionConsumer createConnectionConsumer(Destination arg0,
            String arg1, ServerSessionPool arg2, int arg3) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ConnectionConsumer createDurableConnectionConsumer(Topic arg0,
            String arg1, String arg2, ServerSessionPool arg3, int arg4)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Session createSession(boolean arg0, int arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getClientID() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ExceptionListener getExceptionListener() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ConnectionMetaData getMetaData() throws JMSException
    {
        return _metaData;
    }

    @Override
    public void setClientID(String id) throws JMSException
    {
        checkNotConnected("Cannot set client-id to \"" + id
                + "\"; client-id must be set before the connection is used");
        if (_clientId != null)
        {
            throw new IllegalStateException("client-id has already been set");
        }
        verifyClientID();
        _clientId = id;
    }

    @Override
    public void setExceptionListener(ExceptionListener arg0)
            throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void start() throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void stop() throws JMSException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public ConnectionConsumer createConnectionConsumer(Queue arg0, String arg1,
            ServerSessionPool arg2, int arg3) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public QueueSession createQueueSession(boolean arg0, int arg1)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ConnectionConsumer createConnectionConsumer(Topic arg0, String arg1,
            ServerSessionPool arg2, int arg3) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicSession createTopicSession(boolean arg0, int arg1)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void opened(org.apache.qpid.transport.Connection connection)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void exception(org.apache.qpid.transport.Connection connection,
            ConnectionException exception)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void closed(org.apache.qpid.transport.Connection connection)
    {
        // TODO Auto-generated method stub
    }

    private ConnectionSettings retrieveConnectionSettings(
            BrokerDetails brokerDetail)
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
                    _logger.debug("Applied connection ssl option override, setting UseSsl to: "
                            + connUseSsl);
                }
            }
        }

        return conSettings;
    }

    private void verifyClientID() throws InvalidClientIDException
    {
        if (Boolean.getBoolean(ClientProperties.QPID_VERIFY_CLIENT_ID))
        {
            org.apache.qpid.transport.Session ssn_0_10 = _qpidConnection
                    .createSession(_clientId);
            try
            {
                ssn_0_10.awaitOpen();
            }
            catch (SessionException se)
            {
                // if due to non unique client id for user return false,
                // otherwise wrap and re-throw.
                if (ssn_0_10.getDetachCode() != null
                        && ssn_0_10.getDetachCode() == SessionDetachCode.SESSION_BUSY)
                {
                    throw new InvalidClientIDException(
                            "ClientID must be unique");
                }
                else
                {
                    String msg = "Unexpected SessionException thrown while awaiting session opening";
                    InvalidClientIDException ex = new InvalidClientIDException(
                            msg, SessionDetachCode.UNKNOWN_IDS.name());
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

    // The idle_timeout prop is in milisecs while
    // the new heartbeat prop is in secs
    private int getHeartbeatInterval(BrokerDetails brokerDetail)
    {
        int heartbeat = 0;
        if (brokerDetail.getProperty(BrokerDetails.OPTIONS_IDLE_TIMEOUT) != null)
        {
            _logger.warn("Broker property idle_timeout=<mili_secs> is deprecated, please use heartbeat=<secs>");
            heartbeat = Integer.parseInt(brokerDetail
                    .getProperty(BrokerDetails.OPTIONS_IDLE_TIMEOUT)) / 1000;
        }
        else if (brokerDetail.getProperty(BrokerDetails.OPTIONS_HEARTBEAT) != null)
        {
            heartbeat = Integer.parseInt(brokerDetail
                    .getProperty(BrokerDetails.OPTIONS_HEARTBEAT));
        }
        else if (Integer.getInteger(ClientProperties.IDLE_TIMEOUT_PROP_NAME) != null)
        {
            heartbeat = Integer
                    .getInteger(ClientProperties.IDLE_TIMEOUT_PROP_NAME) / 1000;
            _logger.warn("JVM arg -Didle_timeout=<mili_secs> is deprecated, please use -Dqpid.heartbeat=<secs>");
        }
        else if (Integer.getInteger(ClientProperties.HEARTBEAT) != null)
        {
            heartbeat = Integer.getInteger(ClientProperties.HEARTBEAT,
                    ClientProperties.HEARTBEAT_DEFAULT);
        }
        else
        {
            heartbeat = Integer.getInteger("amqj.heartbeat.delay",
                    ClientProperties.HEARTBEAT_DEFAULT);
        }
        return heartbeat;
    }
}
