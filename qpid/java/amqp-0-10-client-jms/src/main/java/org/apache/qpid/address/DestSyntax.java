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

import java.util.Arrays;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.messaging.util.AddressParser;
import org.apache.qpid.transport.util.Logger;

public enum DestSyntax
{   
    BURL
    {
        public Address parseAddress(String addressString) throws AddressException
        {
            return DestSyntax.parseBURLString(addressString);
        }
    },
    ADDR
    {
        public Address parseAddress(String addressString) throws AddressException
        {
            return DestSyntax.parseAddressString(addressString);
        }
    };

    abstract Address parseAddress(String addressString) throws AddressException;

    public static DestSyntax getDestSyntax(String name)
    {
        try
        {
            return DestSyntax.valueOf(name);
        }
        catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Invalid Destination Syntax Type '" + name + "' should be one of "
                    + Arrays.asList(DestSyntax.values()));
        }
    }
    
    private static final DestSyntax _defaultDestSyntax;
    private static final Logger _logger = Logger.get(DestSyntax.class);
    
    static
    {
        _defaultDestSyntax =
                DestSyntax.getDestSyntax(System.getProperty(ClientProperties.DEST_SYNTAX, DestSyntax.ADDR.name()));
    }

    static DestSyntax getSyntax(String str)
    {
        DestSyntax chosenSyntax = _defaultDestSyntax;
        for(DestSyntax syntax : DestSyntax.values())
        {
            if(str.startsWith(syntax.name() + ":"))
            {                
                chosenSyntax = syntax;
                break;
            }
        }
        
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Based on " + str + " the selected destination syntax is " + chosenSyntax);
        }        
        
        return chosenSyntax;        
    }
    
    public static Address parseAddressString(String str) throws AddressException
    {
        if(str.startsWith(DestSyntax.ADDR.name() + ":"))
        {
            str = str.substring(DestSyntax.ADDR.name().length() + 1);
        }

        org.apache.qpid.messaging.Address rawAddr = new AddressParser(str).parse();
        AddressHelper helper = new AddressHelper(rawAddr);

        return new Address(rawAddr.getName(),rawAddr.getSubject(), helper.isBrowseOnly(), helper.getNode(),helper.getLink());
    }
    
    public static Address parseBURLString(String str) throws AddressException
    {
        return null;
    }
}
