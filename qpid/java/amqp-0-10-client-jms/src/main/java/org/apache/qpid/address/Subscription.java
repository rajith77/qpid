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
import java.util.Map;

public class Subscription
{
    private final Map<String, Object> _args;

    private final boolean _exclusive;

    public Subscription()
    {
        _args = Collections.<String, Object> emptyMap();
        _exclusive = false;
    }

    public Subscription(Map<String, Object> args, boolean exclusive)
    {
        _args = args == null ? Collections.<String, Object> emptyMap() : args;
        _exclusive = exclusive;
    }

    public Map<String, Object> getArgs()
    {
        return _args;
    }

    public boolean isExclusive()
    {
        return _exclusive;
    }
}