/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.exchange;

import junit.framework.Assert;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.message.AMQMessage;
import org.apache.qpid.server.message.MessageMetaData;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.IncomingMessage;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class TopicExchangeTest extends QpidTestCase
{

    private TopicExchange _exchange;
    private VirtualHost _vhost;
    private MessageStore _store;


    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _exchange = new TopicExchange();
        _vhost = BrokerTestHelper.createVirtualHost(getName());
        _store = new MemoryMessageStore();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_vhost != null)
            {
                _vhost.close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    public void testNoRoute() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "a*#b", false, null, false, false, _vhost, null);
        _exchange.registerQueue(new Binding(null, "a.*.#.b",queue, _exchange, null));


        IncomingMessage message = createMessage("a.b");
        routeMessage(message);

        Assert.assertEquals(0, queue.getMessageCount());
    }

    public void testDirectMatch() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "ab", false, null, false, false, _vhost, null);
        _exchange.registerQueue(new Binding(null, "a.b",queue, _exchange, null));


        IncomingMessage message = createMessage("a.b");

        routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.c");

        int queueCount = routeMessage(message);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());
    }


    public void testStarMatch() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "a*", false, null, false, false, _vhost, null);
        _exchange.registerQueue(new Binding(null, "a.*",queue, _exchange, null));


        IncomingMessage message = createMessage("a.b");

        routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.c");

        int queueCount = routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a");


        queueCount = routeMessage(message);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());
    }

    public void testHashMatch() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "a#", false, null, false, false, _vhost, null);
        _exchange.registerQueue(new Binding(null, "a.#",queue, _exchange, null));


        IncomingMessage message = createMessage("a.b.c");

        int queueCount = routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.b");

        queueCount = routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.c");

        queueCount = routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a");

        queueCount = routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("b");


        queueCount = routeMessage(message);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());
    }


    public void testMidHash() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "a", false, null, false, false, _vhost, null);
        _exchange.registerQueue(new Binding(null, "a.*.#.b",queue, _exchange, null));


        IncomingMessage message = createMessage("a.c.d.b");

        routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.c.b");

        routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testMatchafterHash() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "a#", false, null, false, false, _vhost, null);
        _exchange.registerQueue(new Binding(null, "a.*.#.b.c",queue, _exchange, null));


        IncomingMessage message = createMessage("a.c.b.b");

        int queueCount = routeMessage(message);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.a.b.c");

        routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.b.c.b");

        queueCount = routeMessage(message);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.b.c.b.c");

        routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

    }


    public void testHashAfterHash() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "a#", false, null, false, false, _vhost, null);
        _exchange.registerQueue(new Binding(null, "a.*.#.b.c.#.d",queue, _exchange, null));


        IncomingMessage message = createMessage("a.c.b.b.c");

        int queueCount = routeMessage(message);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());


        message = createMessage("a.a.b.c.d");

        routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testHashHash() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "a#", false, null, false, false, _vhost, null);
        _exchange.registerQueue(new Binding(null, "a.#.*.#.d",queue, _exchange, null));


        IncomingMessage message = createMessage("a.c.b.b.c");

        int queueCount = routeMessage(message);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());

        message = createMessage("a.a.b.c.d");

        routeMessage(message);

        Assert.assertEquals(1, queue.getMessageCount());

        Assert.assertEquals("Wrong message recevied", (Object) message.getMessageNumber(), queue.getMessagesOnTheQueue().get(0).getMessage().getMessageNumber());

        queue.deleteMessageFromTop();
        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testSubMatchFails() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "a", false, null, false, false, _vhost, null);
        _exchange.registerQueue(new Binding(null, "a.b.c.d",queue, _exchange, null));


        IncomingMessage message = createMessage("a.b.c");

        int queueCount = routeMessage(message);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());

    }

    private int routeMessage(final IncomingMessage message)
            throws AMQException
    {
        MessageMetaData mmd = message.headersReceived(System.currentTimeMillis());
        message.setStoredMessage(_store.addMessage(mmd));

        message.enqueue(_exchange.route(message));
        AMQMessage msg = new AMQMessage(message.getStoredMessage());
        for(BaseQueue q : message.getDestinationQueues())
        {
            q.enqueue(msg);
        }
        return message.getDestinationQueues().size();
    }

    public void testMoreRouting() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "a", false, null, false, false, _vhost, null);
        _exchange.registerQueue(new Binding(null, "a.b",queue, _exchange, null));


        IncomingMessage message = createMessage("a.b.c");

        int queueCount = routeMessage(message);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());

    }

    public void testMoreQueue() throws AMQException
    {
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "a", false, null, false, false, _vhost, null);
        _exchange.registerQueue(new Binding(null, "a.b",queue, _exchange, null));


        IncomingMessage message = createMessage("a");

        int queueCount = routeMessage(message);
        Assert.assertEquals("Message should not route to any queues", 0, queueCount);

        Assert.assertEquals(0, queue.getMessageCount());

    }

    private IncomingMessage createMessage(String s) throws AMQException
    {
        MessagePublishInfo info = new PublishInfo(new AMQShortString(s));

        IncomingMessage message = new IncomingMessage(info);
        final ContentHeaderBody chb = new ContentHeaderBody();
        BasicContentHeaderProperties props = new BasicContentHeaderProperties();
        chb.setProperties(props);
        message.setContentHeaderBody(chb);


        return message;
    }


    class PublishInfo implements MessagePublishInfo
    {
        private AMQShortString _routingkey;

        PublishInfo(AMQShortString routingkey)
        {
            _routingkey = routingkey;
        }

        public AMQShortString getExchange()
        {
            return null;
        }

        public void setExchange(AMQShortString exchange)
        {

        }

        public boolean isImmediate()
        {
            return false;
        }

        public boolean isMandatory()
        {
            return true;
        }

        public AMQShortString getRoutingKey()
        {
            return _routingkey;
        }
    }
}
