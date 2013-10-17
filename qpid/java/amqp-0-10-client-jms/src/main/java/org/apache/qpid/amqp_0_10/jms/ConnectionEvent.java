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
package org.apache.qpid.amqp_0_10.jms;

public class ConnectionEvent
{
    public enum ConnectionEventType
    {
        OPENED,
        STARTED,
        STOPPED,
        CLOSED,
        EXCEPTION,
        PROTOCOL_CONNECTION_CREATED,
        PROTOCOL_CONNECTION_LOST,
        PRE_FAILOVER,
        POST_FAILOVER
    };

    private Connection _con;

    private ConnectionEventType _eventType;

    private Exception _exception;

    public ConnectionEvent(Connection con, ConnectionEventType eventType, Exception exp)
    {
        _con = con;
        _eventType = eventType;
        _exception = exp;
    }

    public Connection getConnection()
    {
        return _con;
    }

    public ConnectionEventType getEventType()
    {
        return _eventType;
    }

    public Exception getException()
    {
        return _exception;
    }
}