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

import java.util.Enumeration;

import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;

import org.apache.qpid.client.CustomJMSXProperty;
import org.apache.qpid.common.QpidProperties;

public class ConnectionMetaDataImpl implements ConnectionMetaData
{
    ConnectionMetaDataImpl()
    {
    }

    public int getJMSMajorVersion() throws JMSException
    {
        return 1;
    }

    public int getJMSMinorVersion() throws JMSException
    {
        return 1;
    }

    public String getJMSProviderName() throws JMSException
    {
        return "Apache " + QpidProperties.getProductName();
    }

    public String getJMSVersion() throws JMSException
    {
        return "1.1";
    }

    public Enumeration getJMSXPropertyNames() throws JMSException
    {
        return CustomJMSXProperty.asEnumeration();
    }

    public int getProviderMajorVersion() throws JMSException
    {
        return 0;
    }

    public int getProviderMinorVersion() throws JMSException
    {
        return 10;
    }

    public String getProviderVersion() throws JMSException
    {
        return QpidProperties.getProductName() + " (Client: ["
                + getClientVersion() + "] ; Broker [" + getBrokerVersion()
                + "] ; Protocol: [ " + getProtocolVersion() + "] )";
    }

    private String getProtocolVersion()
    {
        return "0-10";
    }

    public String getBrokerVersion()
    {
        // TODO - get broker version
        return "<unkown>";
    }

    public String getClientVersion()
    {
        return QpidProperties.getBuildVersion();
    }
}
