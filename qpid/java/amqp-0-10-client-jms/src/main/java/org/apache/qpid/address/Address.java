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

import static org.apache.qpid.messaging.util.PyPrint.pprint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jms.JMSException;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.ExceptionHelper;

/**
 * Once constructed the Address object should be immutable.
 */
public class Address
{
    private static final Logger _logger = Logger.get(Address.class);

    private static final int MAX_CACHED_ENTRIES = Integer.getInteger(ClientProperties.QPID_MAX_CACHED_ADDR_STRINGS,
            ClientProperties.DEFAULT_MAX_CACHED_ADDR_STRINGS);

    @SuppressWarnings("serial")
    private static final Map<String, Address> ADDRESS_CACHE = Collections
            .synchronizedMap(new LinkedHashMap<String, Address>(MAX_CACHED_ENTRIES + 1, 1.1f, true)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Address> eldest)
                {
                    return size() > MAX_CACHED_ENTRIES;
                }

            });

    private final String _name;

    private final String _subject;

    private final String _myToString;

    private final boolean _browseOnly;

    private final Node _node;

    private final Link _link;

    public static Address parse(String address) throws JMSException
    {
        if (ADDRESS_CACHE.get(address) == null)
        {
            try
            {
                Address addr = DestSyntax.getSyntax(address).parseAddress(address);
                ADDRESS_CACHE.put(address, addr);
                return addr;
            }
            catch (AddressException e)
            {
                throw ExceptionHelper.toJMSException("Error parsing destination string : " + e.getMessage(), e);
            }
        }
        else
        {
            return ADDRESS_CACHE.get(address);
        }
    }

    Address(String name, String subject, boolean browseOnly, Node node, Link link)
    {
        _name = name;
        _subject = subject;
        _node = node;
        _link = link;
        _browseOnly = browseOnly;
        _myToString = String.format("%s/%s; %s %s", pprint(_name), pprint(_subject), node.toString(), link.toString());
    }

    public String getName()
    {
        return _name;
    }

    public String getSubject()
    {
        return _subject;
    }

    public Node getNode()
    {
        return _node;
    }

    public boolean isBrowseOnly()
    {
        return _browseOnly;
    }

    public Link getLink()
    {
        return _link;
    }

    public String toString()
    {
        return _myToString;
    }
}