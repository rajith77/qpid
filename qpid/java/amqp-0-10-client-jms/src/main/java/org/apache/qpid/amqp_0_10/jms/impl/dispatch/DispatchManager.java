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
package org.apache.qpid.amqp_0_10.jms.impl.dispatch;

public interface DispatchManager<K>
{
    public void register(K key);

    public void unregister(K key);

    public void dispatch(Dispatchable<K> dispatchable);

    public void requeue(K key, Dispatchable<K> dispatchable);

    public void sortDispatchQueue(K key);

    public void stopDispatcher(K key);

    public void startDispatcher(K key);

    public void clearDispatcherQueues();

    public void start();

    public void stop();

    public void markStopped();

    public void shutdown();
}