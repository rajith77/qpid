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

public enum FailoverMethod
{
    NO_FAILOVER, BROKER_LIST, FAILOVER_EXCHANGE;

    static final String SINGLE_BROKER_METHOD = "singlebroker";

    static final String ROUND_ROBIN_METHOD = "roundrobin";

    static final String FAILOVER_EXCHANGE_METHOD = "failover_exchange";

    static final String NO_FAILOVER_METHOD = "nofailover";

    public static FailoverMethod getFailoverMethod(String method)
    {
        if (method == null || SINGLE_BROKER_METHOD.equals(method) || ROUND_ROBIN_METHOD.equals(method))
        {
            return BROKER_LIST;
        }
        else if (FAILOVER_EXCHANGE_METHOD.equals(method))
        {
            return FAILOVER_EXCHANGE;
        }
        else if (NO_FAILOVER_METHOD.equals(method))
        {
            return NO_FAILOVER;
        }
        else
        {
            throw new IllegalArgumentException("unknown failover method '" + method + "'. Supported methods are:"
                    + FailoverMethod.values());
        }
    }
}