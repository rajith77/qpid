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
import org.apache.qpid.jms.ConnectionURL;
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

    private long _minRetryInterval = 1000;
    // Default 5 mins
    private long _maxRetryInterval = 1000 * 60 * 5;

    private long _lastRetryInterval = 0;

    private FailoverUnsuccessfulException _exception;

    @Override
    public void init(Connection con)
    {
        _conn = (ConnectionImpl) con;
        _failoverMethod = FailoverMethod.getFailoverMethod(_conn.getConfig().getURL().getFailoverMethod());
        parseRetryIntervals();
        _brokers = getBrokerList();
        _currentBroker = getNextBrokerToConnect();
    }

    @Override
    public void connect() throws FailoverUnsuccessfulException
    {
        if (initialConnAttempted)
        {
            reconnect();
        }
        else
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
                reconnect();
            }
        }
    }

    void reconnect() throws FailoverUnsuccessfulException
    {
        if (_failoverMethod == FailoverMethod.NO_FAILOVER)
        {
            _logger.warn("Failover is disabled.");
            throw new FailoverUnsuccessfulException("Failover is disabled", _exception);
        }
        else
        {
            int attempt = 0;
            while (_currentBroker != null)
            {
                if (_currentBrokerRetries < _currentBroker.getRetries())
                {
                    attempt++;
                    handleRetryInterval(attempt);

                    try
                    {
                        _currentBrokerRetries++;
                        connectToBroker(_currentBroker);
                        return;
                    }
                    catch (FailoverUnsuccessfulException e)
                    {
                        _exception = e;
                    }
                }
                else
                {
                    _currentBroker = getNextBrokerToConnect();
                    _currentBrokerRetries = -1;
                }
            }

            throw new FailoverUnsuccessfulException(
                    "Failover is Unsuccessful. No more brokers to connect to. Last exception linked", _exception);
        }
    }

    void handleRetryInterval(int attempt)
    {
        if (_currentBroker.getConnectDelay() > 0)
        {
            // Log a warning.
            _logger.warn("'connectdelay' broker property is deprecated and hence ignored."
                    + "A min_retry_interval of "
                    + _minRetryInterval
                    / 1000
                    + " secs and max_retry_interval of "
                    + _maxRetryInterval
                    / 1000
                    + " secs used instead."
                    + "Please configure 'min_retry_interval' and 'max_retry_interval' connection properties to change the default");
        }

        long retryInterval = calculateRetryInterval(attempt);        
        _logger.warn("Waiting for : " + retryInterval / 1000 + " secs before retrying connection to " + _currentBroker);
        try
        {
            Thread.sleep(retryInterval);
        }
        catch (InterruptedException e)
        {
            // ignore
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

    long calculateRetryInterval(int attempt)
    {
        if (_lastRetryInterval < _maxRetryInterval)
        {
            double e = 0.5*(Math.pow(2,attempt));
            _lastRetryInterval = (long)e * _minRetryInterval;
            return Math.min(_lastRetryInterval, _maxRetryInterval);
        }
        else
        {
            return _maxRetryInterval;
        }
    }

    void parseRetryIntervals()
    {
        try
        {
            String tmp = _conn.getConfig().getURL().getOption(ConnectionURL.OPTIONS_MIN_RETRY_INTERVAL);
            _minRetryInterval = tmp == null ? _minRetryInterval : Long.parseLong(tmp) * 1000;
        }
        catch (NumberFormatException e)
        {
            _logger.error(e, "Option 'min_interval' contains a non integer value in Connection URL : "
                    + _conn.getConfig().getURL());
        }

        try
        {
            String tmp = _conn.getConfig().getURL().getOption(ConnectionURL.OPTIONS_MAX_RETRY_INTERVAL);
            _maxRetryInterval = tmp == null ? _maxRetryInterval : Long.parseLong(tmp) * 1000;
        }
        catch (NumberFormatException e)
        {
            _logger.error(e, "Option 'min_interval' contains a non integer value in Connection URL : "
                    + _conn.getConfig().getURL());
        }
    }
}