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

import javax.jms.JMSException;

import org.apache.qpid.amqp_0_10.jms.Connection;
import org.apache.qpid.amqp_0_10.jms.FailoverManager;
import org.apache.qpid.amqp_0_10.jms.FailoverUnsuccessfulException;
import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;
import org.apache.qpid.transport.util.Logger;

public class FailoverManagerImpl implements FailoverManager
{
    private static final Logger _logger = Logger.get(FailoverManagerImpl.class);

    private ConnectionImpl _conn;

    private FailoverMethod _failoverMethod;

    private BrokerList _brokers;

    private boolean initialConnAttempted = false;

    private Broker _currentBroker;

    private int _currentBrokerRetries = 0;

    private FailoverUnsuccessfulException _exception;

    @Override
    public void init(Connection con)
    {
        _conn = (ConnectionImpl) con;
        _failoverMethod = FailoverMethod.getFailoverMethod(_conn.getConfig().getURL().getFailoverMethod());
        _brokers = getBrokerList();
        _currentBroker = getNextBrokerToConnect();
    }

    @Override
    public void connect() throws FailoverUnsuccessfulException
    {
        if (!initialConnAttempted)
        {
            initialConnAttempted = true;
            try
            {
                connectToBroker(_currentBroker);
                _logger.warn("Initial Connection Attempt Successfull");
                return;
            }
            catch (FailoverUnsuccessfulException e)
            {
                _exception = e;
                connect();
            }
        }

        switch (_failoverMethod)
        {
        case NO_FAILOVER:
            _logger.warn("Failover is disabled.");
            throw new FailoverUnsuccessfulException("Failover is disabled", _exception);
        default:
            if (_currentBroker == null)
            {
                throw new FailoverUnsuccessfulException(
                        "Failover is Unsuccessful. No more brokers to connect to. Last exception linked", _exception);
            }
            else if (_currentBrokerRetries < _currentBroker.getRetries())
            {
                if (_currentBroker.getConnectDelay() > 0)
                {
                    try
                    {
                        if (_logger.isDebugEnabled())
                        {
                            _logger.debug("Connection delay enabled. Sleeping for : "
                                    + _currentBroker.getConnectDelay() + "ms");
                        }
                        Thread.sleep(_currentBroker.getConnectDelay());
                    }
                    catch (InterruptedException e)
                    {
                        // ignore
                    }
                }
                try
                {
                    _currentBrokerRetries++;
                    connectToBroker(_currentBroker);
                }
                catch (FailoverUnsuccessfulException e)
                {
                    _exception = e;
                    connect();
                }
            }
            else
            {
                _currentBroker = getNextBrokerToConnect();
                connect();
            }
        }
    }

    Broker getNextBrokerToConnect()
    {
        return _brokers.getNextBroker();
    }

    void connectToBroker(Broker broker) throws FailoverUnsuccessfulException
    {
        try
        {
            _conn.connect(broker.getSettings());
        }
        catch (JMSException e)
        {
            throw new FailoverUnsuccessfulException("Connection unsuccessful", e);
        }
    }

    BrokerList getBrokerList()
    {
        switch (_failoverMethod)
        {
        case FAILOVER_EXCHANGE:
            return new FailoverExchangeBrokerList(_conn);
        default:
            return new URLBrokerList(_conn);
        }
    }
}