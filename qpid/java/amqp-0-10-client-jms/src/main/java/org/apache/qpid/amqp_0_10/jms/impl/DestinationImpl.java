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

import java.util.WeakHashMap;

import javax.jms.Destination;
import javax.jms.JMSException;

import org.apache.qpid.address.Address;

public class DestinationImpl implements Destination
{
    private static final WeakHashMap<String, DestinationImpl> DESTINATION_CACHE =
            new WeakHashMap<String, DestinationImpl>();

    private final Address _address;
    
    protected DestinationImpl(String addr) throws JMSException
    {
        _address = Address.parse(addr);
    }
    
    protected Address getAddress()
    {
        return _address;
    }
    
    @Override
    public int hashCode()
    {
        return _address.hashCode();
    }

    @Override
    public boolean equals(final Object obj)
    {
        return obj != null
               && obj.getClass() == getClass()
               && _address.equals(((DestinationImpl)obj)._address);
    }

    public static synchronized DestinationImpl createDestination(final String address) throws JMSException
    {
        DestinationImpl destination = DESTINATION_CACHE.get(address);
        if(destination == null)
        {
            destination = new DestinationImpl(address);
            DESTINATION_CACHE.put(address, destination);
        }
        return destination;
    } 
}