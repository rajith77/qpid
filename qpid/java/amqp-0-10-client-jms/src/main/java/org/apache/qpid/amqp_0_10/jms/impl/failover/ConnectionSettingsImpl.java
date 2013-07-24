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
package org.apache.qpid.amqp_0_10.jms.impl.failover;

import java.util.Map;

import org.apache.qpid.client.AMQBrokerDetails;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.transport.DefaultConnectionSettingsImpl;
import org.apache.qpid.transport.util.Logger;

public class ConnectionSettingsImpl extends DefaultConnectionSettingsImpl
{
    private static final Logger _logger = Logger.get(ConnectionSettingsImpl.class);

    ConnectionSettingsImpl(AMQConnectionURL url, AMQBrokerDetails broker, Map<String, Object> clientProps)
    {
        super();
        _host = broker.getHost();
        _port = broker.getPort();

        // ------------ sasl options ---------------
        if (broker.getProperty(BrokerDetails.OPTIONS_SASL_MECHS) != null)
        {
            _saslMechs = broker.getProperty(BrokerDetails.OPTIONS_SASL_MECHS);
        }

        // Sun SASL Kerberos client uses the
        // protocol + servername as the service key.

        if (broker.getProperty(BrokerDetails.OPTIONS_SASL_PROTOCOL_NAME) != null)
        {
            _saslProtocol = broker.getProperty(BrokerDetails.OPTIONS_SASL_PROTOCOL_NAME);
        }

        if (broker.getProperty(BrokerDetails.OPTIONS_SASL_SERVER_NAME) != null)
        {
            _saslServerName = broker.getProperty(BrokerDetails.OPTIONS_SASL_SERVER_NAME);
        }

        _useSASLEncryption = broker.getBooleanProperty(BrokerDetails.OPTIONS_SASL_ENCRYPTION);

        // ------------- ssl options ---------------------
        _useSSL = broker.getBooleanProperty(BrokerDetails.OPTIONS_SSL);

        if (broker.getProperty(BrokerDetails.OPTIONS_TRUST_STORE) != null)
        {
            _trustStorePath = broker.getProperty(BrokerDetails.OPTIONS_TRUST_STORE);
        }

        if (broker.getProperty(BrokerDetails.OPTIONS_TRUST_STORE_PASSWORD) != null)
        {
            _trustStorePassword = broker.getProperty(BrokerDetails.OPTIONS_TRUST_STORE_PASSWORD);
        }

        if (broker.getProperty(BrokerDetails.OPTIONS_KEY_STORE) != null)
        {
            _keyStorePath = broker.getProperty(BrokerDetails.OPTIONS_KEY_STORE);
        }

        if (broker.getProperty(BrokerDetails.OPTIONS_KEY_STORE_PASSWORD) != null)
        {
            _keyStorePassword = broker.getProperty(BrokerDetails.OPTIONS_KEY_STORE_PASSWORD);
        }

        if (broker.getProperty(BrokerDetails.OPTIONS_SSL_CERT_ALIAS) != null)
        {
            _certAlias = broker.getProperty(BrokerDetails.OPTIONS_SSL_CERT_ALIAS);
        }
        // ----------------------------

        _verifyHostname = broker.getBooleanProperty(BrokerDetails.OPTIONS_SSL_VERIFY_HOSTNAME);

        if (broker.getProperty(BrokerDetails.OPTIONS_TCP_NO_DELAY) != null)
        {
            _tcpNodelay = broker.getBooleanProperty(BrokerDetails.OPTIONS_TCP_NO_DELAY, true);
        }

        _connectTimeout = broker.lookupConnectTimeout();

        _clientProperties = clientProps;

        _heartbeatInterval = getHeartbeatInterval(broker);

        // Check connection-level ssl override setting
        String connectionSslOption = url.getOption(ConnectionURL.OPTIONS_SSL);
        if (connectionSslOption != null)
        {
            boolean connUseSsl = Boolean.parseBoolean(connectionSslOption);
            boolean brokerlistUseSsl = _useSSL;

            if (connUseSsl != brokerlistUseSsl)
            {
                _useSSL = connUseSsl;

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Applied connection ssl option override, setting UseSsl to: " + connUseSsl);
                }
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

}