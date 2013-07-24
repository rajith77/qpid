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

import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.transport.util.Logger;

/**
 * Helper class to hold all the connection level settings. Used as a means to
 * reduce clutter in ConnectionImpl and also to keep AMQConnection and
 * AMQBrokerDetails away from ConnectionImpl.
 */
public class ConnectionConfig
{
    private static final Logger _logger = Logger.get(ConnectionConfig.class);

    private final ConnectionImpl _conn;

    private final AMQConnectionURL _url;

    ConnectionConfig(ConnectionImpl conn, AMQConnectionURL url)
    {
        _conn = conn;
        _url = url;
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
        // TODO if connection property not null return that else, get the
        // default.
        return Integer.getInteger(ClientProperties.QPID_DISPATCHER_COUNT, ClientProperties.DEFAULT_DISPATCHER_COUNT);
    }

    /*
     * Unfortunately AMQCallback handler needs it, so need to expose it.
     */
    public AMQConnectionURL getURL()
    {
        return _url;
    }
}