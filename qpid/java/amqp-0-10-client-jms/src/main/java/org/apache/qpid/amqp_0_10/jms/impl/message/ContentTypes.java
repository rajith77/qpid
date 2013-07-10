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
package org.apache.qpid.amqp_0_10.jms.impl.message;

public interface ContentTypes
{
    public static final String BINARY = "application/octet-stream";

    public static final String TEXT_PLAIN = "text/plain";

    public static final String TEXT_XML = "text/xml";

    public static final String AMQP_MAP = "amqp/map";

    public static final String AMQP_0_10_MAP = "amqp-0-10/map";

    public static final String AMQP_LIST = "amqp/list";

    public static final String AMQP_0_10_LIST = "amqp-0-10/list";

    public static final String JAVA_OBJECT = "application/java-object-stream";
}