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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.transport.ProtocolVersionException;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.util.ExceptionHelper;

public class ConnectionFactoryImpl implements ConnectionFactory,
        QueueConnectionFactory, TopicConnectionFactory
{
    private static final Logger _logger = Logger.get(ConnectionFactoryImpl.class);

    private AMQConnectionURL _url;

    public ConnectionFactoryImpl()
    {
    }

    public ConnectionFactoryImpl(String url) throws URLSyntaxException
    {
        _url = new AMQConnectionURL(url);
    }

    @Override
    public Connection createConnection() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Connection createConnection(String user, String pass)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicConnection createTopicConnection() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TopicConnection createTopicConnection(String user, String pass)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public QueueConnection createQueueConnection() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public QueueConnection createQueueConnection(String user, String pass)
            throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    private Connection createAMQPConnection() throws JMSException
    {
        try
        {
            return new ConnectionImpl(_url);
        }
        catch (JMSException ex)
        {
            if (ex.getCause() instanceof ProtocolVersionException)
            {
                try
                {
                    return new AMQConnection(_url);
                }
                catch (AMQException e)
                {
                    throw ExceptionHelper.toJMSException(
                            "Error creating connection", e.getErrorCode()
                                    .toString(), e);
                }
            }
            else
            {
                throw ex;
            }
        }
    }
}
