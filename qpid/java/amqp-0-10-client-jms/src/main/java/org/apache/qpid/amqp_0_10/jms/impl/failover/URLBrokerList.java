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

import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.transport.util.Logger;

public class URLBrokerList implements BrokerList
{
    private static final Logger _logger = Logger.get(URLBrokerList.class);

    private final List<Broker> _brokers;

    private final int CYCLE_COUNT;

    private int _currentCycleCount = 0;

    private int _currentBrokerIndex = 0;

    URLBrokerList(ConnectionImpl conn)
    {
        int count = conn.getConfig().getURL().getBrokerCount();
        _brokers = new ArrayList<Broker>(count);
        for (int i = 0; i < count; i++)
        {
            _brokers.add(Broker.getBroker(conn, conn.getConfig().getURL().getBrokerDetails(i)));
        }

        String cycleOption = conn.getConfig().getURL().getFailoverOption(ConnectionURL.OPTIONS_FAILOVER_CYCLE);
        int cycleCount = 0;
        try
        {
            cycleCount = cycleOption == null ? 0 : Integer.parseInt(cycleOption);
        }
        catch (NumberFormatException e)
        {
            _logger.error(e, "Option 'cyclecount' contains a non integer value in Connection URL : "
                    + conn.getConfig().getURL());
        }
        CYCLE_COUNT = cycleCount;
    }

    @Override
    public Broker getNextBroker()
    {
        if (_currentBrokerIndex >= _brokers.size())
        {
            if (_currentCycleCount < CYCLE_COUNT)
            {
                _currentBrokerIndex = 0;
                _currentCycleCount++;
                return getNextBroker();
            }
            else
            {
                return null;
            }
        }
        else
        {
            return _brokers.get(_currentBrokerIndex++);
        }
    }
}