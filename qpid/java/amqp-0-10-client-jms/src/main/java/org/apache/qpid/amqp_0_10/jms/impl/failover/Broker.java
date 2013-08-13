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

import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;
import org.apache.qpid.client.AMQBrokerDetails;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.util.Logger;

public class Broker
{
    private static final Logger _logger = Logger.get(Broker.class);

    private final ConnectionSettings _settings;

    private final int _retries;

    private final long _connectDelay;

    Broker(ConnectionSettings settings, int retries, long connectDelay)
    {
        _settings = settings;
        _retries = retries;
        _connectDelay = connectDelay;
    }

    ConnectionSettings getSettings()
    {
        return _settings;
    }

    int getRetries()
    {
        return _retries;
    }

    long getConnectDelay()
    {
        return _connectDelay;
    }

    public static Broker getBroker(ConnectionImpl conn, BrokerDetails broker)
    {
        // Pass client name from connection URL
        Map<String, Object> clientProps = new HashMap<String, Object>();
        try
        {
            clientProps.put(ConnectionStartProperties.CLIENT_ID_0_10, conn.getClientID());
        }
        catch (JMSException e)
        {
        }

        ConnectionSettings settings = new ConnectionSettingsImpl(conn.getConfig().getURL(), (AMQBrokerDetails) broker,
                clientProps);

        int retries = 0;
        try
        {
            retries = broker.getProperty(BrokerDetails.OPTIONS_RETRY) == null ? 0 : Integer.parseInt(broker
                    .getProperty(BrokerDetails.OPTIONS_RETRY));
        }
        catch (NumberFormatException e)
        {
            _logger.error(e, "Option 'retry' contains a non integer value in Connection URL : "
                    + conn.getConfig().getURL());
        }

        long connectDelay = 0;
        try
        {
            connectDelay = broker.getProperty(BrokerDetails.OPTIONS_CONNECT_DELAY) == null ? 0 : Long.parseLong(broker
                    .getProperty(BrokerDetails.OPTIONS_CONNECT_DELAY));
        }
        catch (NumberFormatException e)
        {
            _logger.error(e, "Option 'connectdelay' contains a non integer value in Connection URL : "
                    + conn.getConfig().getURL());
        }

        return new Broker(settings, retries, connectDelay);
    }

    @Override
    public String toString()
    {
        return _settings.getProtocol() + "//" + _settings.getHost() + ":" + _settings.getPort();
    }
}