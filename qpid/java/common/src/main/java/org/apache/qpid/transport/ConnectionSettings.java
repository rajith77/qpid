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
package org.apache.qpid.transport;

import java.util.Map;

public interface ConnectionSettings
{
    // TODO why is this property here ???
    static final String WILDCARD_ADDRESS = "*";

    public boolean isTcpNodelay();

    public int getHeartbeatInterval();

    public String getProtocol();

    public String getHost();

    public int getPort();

    public String getVhost();

    public String getUsername();

    public String getPassword();

    public boolean isUseSSL();

    public boolean isUseSASLEncryption();

    public String getSaslMechs();

    public String getSaslProtocol();

    public String getSaslServerName();

    public int getMaxChannelCount();

    public int getMaxFrameSize();

    public Map<String, Object> getClientProperties();

    public String getKeyStorePath();

    public String getKeyStorePassword();

    public String getKeyStoreType();

    public String getTrustStorePath();

    public String getTrustStorePassword();

    public String getCertAlias();

    public boolean isVerifyHostname();

    public String getKeyManagerFactoryAlgorithm();

    public String getTrustManagerFactoryAlgorithm();

    public String getTrustStoreType();

    public int getConnectTimeout();

    public int getReadBufferSize();

    public int getWriteBufferSize();

    public ConnectionSettings copy();
}