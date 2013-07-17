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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.qpid.configuration.Accessor;
import org.apache.qpid.configuration.Accessor.MapAccessor;
import org.apache.qpid.messaging.Address;

/**
 * Utility class for extracting information from the address class
 */
public class AddressHelper
{
    public static final String NODE = "node";

    public static final String LINK = "link";

    public static final String X_DECLARE = "x-declare";

    public static final String X_BINDINGS = "x-bindings";

    public static final String X_SUBSCRIBE = "x-subscribes";

    public static final String CREATE = "create";

    public static final String ASSERT = "assert";

    public static final String DELETE = "delete";

    public static final String FILTER = "filter";

    public static final String NO_LOCAL = "no-local";

    public static final String DURABLE = "durable";

    public static final String EXCLUSIVE = "exclusive";

    public static final String AUTO_DELETE = "auto-delete";

    public static final String TYPE = "type";

    public static final String ALT_EXCHANGE = "alternate-exchange";

    public static final String BINDINGS = "bindings";

    public static final String BROWSE = "browse";

    public static final String MODE = "mode";

    public static final String CAPACITY = "capacity";

    public static final String CAPACITY_SOURCE = "source";

    public static final String CAPACITY_TARGET = "target";

    public static final String NAME = "name";

    public static final String EXCHANGE = "exchange";

    public static final String QUEUE = "queue";

    public static final String KEY = "key";

    public static final String ARGUMENTS = "arguments";

    public static final String RELIABILITY = "reliability";

    private Address _address;

    private Accessor _addressPropAccess;

    private Accessor _nodePropAccess;

    private Accessor _linkPropAccess;

    @SuppressWarnings("rawtypes")
    private Map _addressPropMap;

    @SuppressWarnings("rawtypes")
    private Map _nodePropMap;

    @SuppressWarnings("rawtypes")
    private Map _linkPropMap;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public AddressHelper(Address address)
    {
        this._address = address;
        this._addressPropMap = address.getOptions();
        this._addressPropAccess = new MapAccessor(_addressPropMap);
        this._nodePropMap = address.getOptions() == null || address.getOptions().get(NODE) == null ? null
                : (Map) address.getOptions().get(NODE);

        if (_nodePropMap != null)
        {
            _nodePropAccess = new MapAccessor(_nodePropMap);
        }

        this._linkPropMap = address.getOptions() == null || address.getOptions().get(LINK) == null ? null
                : (Map) address.getOptions().get(LINK);

        if (_linkPropMap != null)
        {
            _linkPropAccess = new MapAccessor(_linkPropMap);
        }
    }

    AddressPolicy getCreate()
    {
        return AddressPolicy.getAddressPolicy(_addressPropAccess.getString(CREATE));
    }

    AddressPolicy getAssert()
    {
        return AddressPolicy.getAddressPolicy(_addressPropAccess.getString(ASSERT));
    }

    AddressPolicy getDelete()
    {
        return AddressPolicy.getAddressPolicy(_addressPropAccess.getString(DELETE));
    }

    boolean isBrowseOnly()
    {
        String mode = _addressPropAccess.getString(MODE);
        return mode != null && mode.equals(BROWSE) ? true : false;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    List<Binding> getBindings(Map props)
    {
        List<Binding> bindings = new ArrayList<Binding>();
        List<Map> bindingList = (props == null) ? Collections.EMPTY_LIST : (List<Map>) props.get(X_BINDINGS);
        if (bindingList != null)
        {
            for (Map bindingMap : bindingList)
            {
                Binding binding = new Binding((String) bindingMap.get(EXCHANGE), (String) bindingMap.get(QUEUE),
                        (String) bindingMap.get(KEY), bindingMap.get(ARGUMENTS) == null ? Collections.EMPTY_MAP
                                : (Map<String, Object>) bindingMap.get(ARGUMENTS));
                bindings.add(binding);
            }
        }
        return bindings;
    }

    @SuppressWarnings("rawtypes")
    Map getDeclareArgs(Map props)
    {
        if (props != null && props.get(X_DECLARE) != null)
        {
            return (Map) props.get(X_DECLARE);

        }
        else
        {
            return null;
        }
    }

    NodeType getNodeType() throws AddressException
    {
        if (_nodePropAccess == null || _nodePropAccess.getString(TYPE) == null)
        {
            // need to query and figure out
            return NodeType.UNDEFINED;
        }
        else if (_nodePropAccess.getString(TYPE).equals("queue"))
        {
            return NodeType.QUEUE;
        }
        else if (_nodePropAccess.getString(TYPE).equals("topic"))
        {
            return NodeType.TOPIC;
        }
        else
        {
            throw new AddressException("unkown exchange type");
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Node getNode()
    {   
        AddressPolicy create = getCreate();
        AddressPolicy xssert = getAssert();
        AddressPolicy delete = getDelete();
     
        Node node;
        if (_nodePropAccess == null)
        {
            node = new Node(_address.getName(), create, xssert, delete);
        }
        else
        {
            Map xDeclareMap = getDeclareArgs(_nodePropMap);
            MapAccessor xDeclareMapAccessor = new MapAccessor(xDeclareMap);

            node = new Node(_address.getName(),
                            getNodeType(),
                            getBooleanProperty(_nodePropAccess,DURABLE,false),
                            create,
                            xssert,
                            delete,
                            getBooleanProperty(xDeclareMapAccessor,AUTO_DELETE,false),
                            getBooleanProperty(xDeclareMapAccessor,EXCLUSIVE,false),
                            xDeclareMapAccessor.getString(ALT_EXCHANGE),
                            xDeclareMapAccessor.getString(TYPE),
                            (Map<String,Object>)xDeclareMap.get(ARGUMENTS),
                            getBindings(_nodePropMap));
        }
        return node;
    }

    // This should really be in the Accessor interface
    boolean getBooleanProperty(Accessor access, String propName, boolean defaultValue)
    {
        Boolean result = access.getBoolean(propName);
        return (result == null) ? defaultValue : result.booleanValue();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Link getLink()
    {
        Link link;
        ;
        if (_linkPropAccess == null)
        {
            link = new Link();
        }
        else
        {
            int prodCapacity = 0;
            int consCapacity = 0;

            if (_linkPropAccess.getMap(CAPACITY) != null)
            {
                MapAccessor capacityProps = new MapAccessor(_linkPropAccess.getMap(CAPACITY));

                consCapacity = capacityProps.getInt(CAPACITY_SOURCE) == null ?
                               0 : capacityProps.getInt(CAPACITY_SOURCE);

                prodCapacity = capacityProps.getInt(CAPACITY_TARGET) == null ?
                               0 : capacityProps.getInt(CAPACITY_TARGET);
            }
            else
            {
                int cap = _linkPropAccess.getInt(CAPACITY) == null ? 0 : _linkPropAccess.getInt(CAPACITY);
                prodCapacity = cap;
                consCapacity = cap;
            }

            Subscription subscription;
            if (_linkPropAccess.getMap(X_SUBSCRIBE) != null)
            {
                MapAccessor x_subscribe = new MapAccessor(_linkPropAccess.getMap(X_SUBSCRIBE));
                subscription = new Subscription((Map<String, Object>) x_subscribe.getMap(ARGUMENTS),
                                                getBooleanProperty(x_subscribe, EXCLUSIVE, false));
            }
            else
            {
                subscription = new Subscription();
            }

            Map xDeclareMap = getDeclareArgs(_linkPropMap);
            SubscriptionQueue subQueue;
            if (!xDeclareMap.isEmpty() && xDeclareMap.containsKey(ARGUMENTS))
            {
                MapAccessor xDeclareMapAccessor = new MapAccessor(xDeclareMap);
                subQueue = new SubscriptionQueue((Map<String, Object>) xDeclareMap.get(ARGUMENTS),
                                                 getBooleanProperty(xDeclareMapAccessor, AUTO_DELETE, true),
                                                 getBooleanProperty(xDeclareMapAccessor, EXCLUSIVE,true),
                                                 xDeclareMapAccessor.getString(ALT_EXCHANGE));
            }
            else
            {
                subQueue = new SubscriptionQueue();
            }

            link = new Link(_linkPropAccess.getString(NAME),
                            getBooleanProperty(_linkPropAccess, DURABLE, false),
                            Reliability.getReliability(_linkPropAccess.getString(RELIABILITY)),
                            prodCapacity,
                            consCapacity,
                            subscription,
                            getBindings(_linkPropMap),
                            subQueue);
        }

        return link;
    }
}
