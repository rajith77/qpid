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
import java.util.Map;

public class Node
{
    private final String _name;

    private final NodeType _type;

    private final AddressPolicy _createPolicy;

    private final AddressPolicy _assertPolicy;

    private final AddressPolicy _deletePolicy;

    private boolean _durable;

    private boolean _autoDelete;

    private boolean _exclusive;

    private String _alternateExchange;

    private String _exchangeType = "topic";

    private final Map<String, Object> _declareArgs;

    private final List<Binding> _bindings;

    public Node(String name, AddressPolicy createPolicy, AddressPolicy assertPolicy, AddressPolicy deletePolicy)
    {
        _name = name;
        _durable = false;
        _type = NodeType.UNDEFINED;
        _createPolicy = createPolicy;
        _assertPolicy = assertPolicy;
        _deletePolicy = deletePolicy;
        _autoDelete = false;
        _exclusive = false;
        _alternateExchange = null;
        _exchangeType = null;
        _declareArgs = Collections.<String, Object> emptyMap();
        _bindings = Collections.<Binding> emptyList();
    }

    public Node(String name, 
                NodeType type,
                boolean durable,
                AddressPolicy createPolicy,
                AddressPolicy assertPolicy,
                AddressPolicy deletePolicy,
                boolean autoDelete,
                boolean exclusive,
                String alternateExchange,
                String exchangeType,
                Map<String, Object> declareArgs,
                List<Binding> bindings)
    {
        _name = name;
        _durable = durable;
        _type = type == null ? NodeType.QUEUE : type;
        _createPolicy = createPolicy == null ? AddressPolicy.NEVER : createPolicy;
        _assertPolicy = assertPolicy == null ? AddressPolicy.NEVER : assertPolicy;
        _deletePolicy = deletePolicy == null ? AddressPolicy.NEVER : deletePolicy;
        _autoDelete  = autoDelete;
        _exclusive = exclusive;
        _alternateExchange = alternateExchange;
        _exchangeType = exchangeType;
        _declareArgs = declareArgs == null ? Collections.<String, Object> emptyMap() : Collections
                .unmodifiableMap(declareArgs);
        _bindings = bindings == null ? Collections.<Binding> emptyList() : Collections.unmodifiableList(bindings);
    }

    public String getName()
    {
        return _name;
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public NodeType getType()
    {
        return _type;
    }

    public AddressPolicy getCreatePolicy()
    {
        return _createPolicy;
    }

    public AddressPolicy getAssertPolicy()
    {
        return _assertPolicy;
    }

    public AddressPolicy getDeletePolicy()
    {
        return _deletePolicy;
    }

    public boolean isAutoDelete()
    {
        return _autoDelete;
    }

    public boolean isExclusive()
    {
        return _exclusive;
    }

    public String getAlternateExchange()
    {
        return _alternateExchange;
    }

    public String getExchangeType()
    {
        return _exchangeType;
    }

    public Map<String, Object> getDeclareArgs()
    {
        return _declareArgs;
    }

    public List<Binding> getBindings()
    {
        return _bindings;
    }
}