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

import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;

import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.util.Logger;

/**
 * Helper class to hold all the connection level settings. Used to reduce
 * clutter in ConnectionImpl
 */
public class ConnectionConfig
{
    private static final Logger _logger = Logger.get(ConnectionConfig.class);

    private final ConnectionImpl _conn;

    private final AMQConnectionURL _url;

    ConnectionConfig(ConnectionImpl conn)
    {
        _conn = conn;
        _url = conn.getConnectionURL();
    }

    public int getMaxPrefetch()
    {
        return Integer.parseInt(ClientProperties.MAX_PREFETCH_DEFAULT);
    }

    public PublishMode getPublishMode()
    {
        return null;
    }

    public int getDispatcherCount()
    {
        //TODO if connection property not null return that else, get the default.
        return Integer.getInteger(ClientProperties.QPID_DISPATCHER_COUNT,
                ClientProperties.DEFAULT_DISPATCHER_COUNT);
    }

    public ConnectionSettings retrieveConnectionSettings(BrokerDetails brokerDetail)
    {
        ConnectionSettings conSettings = brokerDetail.buildConnectionSettings();

        conSettings.setVhost(_url.getVirtualHost());
        conSettings.setUsername(_url.getUsername());
        conSettings.setPassword(_url.getPassword());

        // Pass client name from connection URL
        Map<String, Object> clientProps = new HashMap<String, Object>();
        try
        {
            clientProps.put(ConnectionStartProperties.CLIENT_ID_0_10, _conn.getClientID());
        }
        catch (JMSException e)
        {            
        }
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