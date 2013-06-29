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
package org.apache.qpid.address;

import java.util.Collections;
import java.util.List;

public class Link
{
    private final String _name;

    private final boolean _durable;

    private final int _consumerCapacity;

    private final int _producerCapacity;

    private final Reliability _reliability;

    private final Subscription _subscription;
    
    private final List<Binding> _bindings;
    
    private final SubscriptionQueue _subscriptionQueue;

    public Link()
    {
        _name = null;
        _durable = false;
        _reliability = Reliability.AT_LEAST_ONCE;
        _producerCapacity = 0;
        _consumerCapacity = 0;
        _subscription = new Subscription();
        _bindings = Collections.<Binding>emptyList();
        _subscriptionQueue = new SubscriptionQueue();
    }

    public Link(String name, boolean durable, Reliability reliability, int producerCapacity, int consumerCapacity,
            Subscription subscription, List<Binding> binding, SubscriptionQueue subscriptionQueue)
    {
        _name = name;
        _durable = durable;
        _reliability = reliability;
        _producerCapacity = producerCapacity;
        _consumerCapacity = consumerCapacity;
        _subscription = subscription;
        _bindings = binding == null ? Collections.<Binding>emptyList() : Collections.unmodifiableList(binding);
        _subscriptionQueue = subscriptionQueue;
    }

    public Reliability getReliability()
    {
        return _reliability;
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public int getConsumerCapacity()
    {
        return _consumerCapacity;
    }

    public int getProducerCapacity()
    {
        return _producerCapacity;
    }

    public String getName()
    {
        return _name;
    }

    public Subscription getSubscription()
    {
        return _subscription;
    }

    public List<Binding> getBindings()
    {
        return _bindings;
    }

    public SubscriptionQueue getSubscribeQueue()
    {
        return _subscriptionQueue;
    }
}