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
package org.apache.qpid.client;

import java.util.Map;

import org.apache.qpid.transport.DefaultConnectionSettingsImpl;

/**
 * A ConnectionSettings object can only be associated with one Connection
 * object. I have added an assertion that will throw an exception if it is used
 * by more than on Connection
 * 
 * TODO It would be good if the constructor takes in an AMQConnectionRL, and an
 * AMQBroker Detail, and then transfer the buildConnectionSettings() logic into
 * the constructor.
 */
class AMQConnectionSettingsImpl extends DefaultConnectionSettingsImpl
{
    protected AMQConnectionSettingsImpl()
    {
    }

    void setTcpNodelay(boolean tcpNodelay)
    {
        _tcpNodelay = tcpNodelay;
    }

    void setHeartbeatInterval(int heartbeatInterval)
    {
        _heartbeatInterval = heartbeatInterval;
    }

    void setProtocol(String protocol)
    {
        _protocol = protocol;
    }

    void setHost(String host)
    {
        _host = host;
    }

    void setPort(int port)
    {
        _port = port;
    }

    void setVhost(String vhost)
    {
        _vhost = vhost;
    }

    void setUsername(String username)
    {
        _username = username;
    }

    void setPassword(String password)
    {
        _password = password;
    }

    void setUseSSL(boolean useSSL)
    {
        _useSSL = useSSL;
    }

    void setUseSASLEncryption(boolean useSASLEncryption)
    {
        _useSASLEncryption = useSASLEncryption;
    }

    void setSaslMechs(String saslMechs)
    {
        _saslMechs = saslMechs;
    }

    void setSaslProtocol(String saslProtocol)
    {
        _saslProtocol = saslProtocol;
    }

    void setSaslServerName(String saslServerName)
    {
        _saslServerName = saslServerName;
    }

    void setMaxChannelCount(int maxChannelCount)
    {
        _maxChannelCount = maxChannelCount;
    }

    void setMaxFrameSize(int maxFrameSize)
    {
        _maxFrameSize = maxFrameSize;
    }

    void setClientProperties(final Map<String, Object> clientProperties)
    {
        _clientProperties = clientProperties;
    }

    void setKeyStorePath(String keyStorePath)
    {
        _keyStorePath = keyStorePath;
    }

    void setKeyStorePassword(String keyStorePassword)
    {
        _keyStorePassword = keyStorePassword;
    }

    void setKeyStoreType(String keyStoreType)
    {
        _keyStoreType = keyStoreType;
    }

    void setTrustStorePath(String trustStorePath)
    {
        _trustStorePath = trustStorePath;
    }

    void setTrustStorePassword(String trustStorePassword)
    {
        _trustStorePassword = trustStorePassword;
    }

    void setCertAlias(String certAlias)
    {
        _certAlias = certAlias;
    }

    void setVerifyHostname(boolean verifyHostname)
    {
        _verifyHostname = verifyHostname;
    }

    void setKeyManagerFactoryAlgorithm(String keyManagerFactoryAlgorithm)
    {
        _keyManagerFactoryAlgorithm = keyManagerFactoryAlgorithm;
    }

    void setTrustManagerFactoryAlgorithm(String trustManagerFactoryAlgorithm)
    {
        _trustManagerFactoryAlgorithm = trustManagerFactoryAlgorithm;
    }

    void setTrustStoreType(String trustStoreType)
    {
        _trustStoreType = trustStoreType;
    }

    void setConnectTimeout(int connectTimeout)
    {
        _connectTimeout = connectTimeout;
    }

    void setReadBufferSize(int readBufferSize)
    {
        _readBufferSize = readBufferSize;
    }

    void setWriteBufferSize(int writeBufferSize)
    {
        _writeBufferSize = writeBufferSize;
    }
}