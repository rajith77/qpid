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

/**
 * If an application wants to override default failover behavior, It can
 * implement this interface and pass the implementation class name either as a
 * connection parameter or specify it using
 * -Dqpid.failover_manager=<class-name>.
 * 
 */
public interface FailoverManager
{
    public void init(Connection con);

    /**
     * This method should either call @See Connection.connect(ConnectionSettings
     * settings) method, with the next broker to connect to, or throw
     * FailoverUnsuccessfulException
     * 
     * @throws FailoverUnsuccessfulException
     */
    public void connect() throws FailoverUnsuccessfulException;
}