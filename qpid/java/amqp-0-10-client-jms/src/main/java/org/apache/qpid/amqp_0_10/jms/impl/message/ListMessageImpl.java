package org.apache.qpid.amqp_0_10.jms.impl.message;

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
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.jms.JMSException;

import org.apache.qpid.amqp_0_10.jms.impl.SessionImpl;
import org.apache.qpid.jms.ListMessage;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;

public class ListMessageImpl extends StreamMessageImpl implements ListMessage
{

    public ListMessageImpl()
    {
        super();
    }

    public ListMessageImpl(SessionImpl ssn, int transferId, String consumerId, DeliveryProperties deliveryProps,
            MessageProperties msgProps, ByteBuffer data)
    {
        super(ssn, transferId, consumerId, deliveryProps, msgProps, data);
    }

    @Override
    public boolean add(Object e) throws JMSException
    {
        isContentWritable();
        return _list.add(e);
    }

    @Override
    public void add(int index, Object e) throws JMSException
    {
        isContentWritable();
        _list.add(index, e);
    }

    @Override
    public boolean contains(Object e) throws JMSException
    {
        isContentReadable();
        return _list.contains(e);
    }

    @Override
    public Object get(int index) throws JMSException
    {
        isContentReadable();
        return _list.get(index);
    }

    @Override
    public int indexOf(Object e) throws JMSException
    {
        isContentReadable();
        return _list.indexOf(e);
    }

    @Override
    public Iterator<Object> iterator() throws JMSException
    {
        isContentReadable();
        return _list.iterator();
    }

    @Override
    public Object remove(int index) throws JMSException
    {
        isContentWritable();
        return _list.remove(index);
    }

    @Override
    public boolean remove(Object e) throws JMSException
    {
        isContentWritable();
        return _list.remove(e);
    }

    @Override
    public Object set(int index, Object e) throws JMSException
    {
        isContentWritable();
        return _list.set(index, e);
    }

    @Override
    public int size() throws JMSException
    {
        return _list.size();
    }

    @Override
    public Object[] toArray() throws JMSException
    {
        isContentReadable();
        return _list.toArray();
    }

    @Override
    public List<Object> asList() throws JMSException
    {
        isContentReadable();
        return Collections.unmodifiableList(_list);
    }

    @Override
    public void setList(List<Object> l) throws JMSException
    {
        isContentWritable();
        _list = l;
    }
}
