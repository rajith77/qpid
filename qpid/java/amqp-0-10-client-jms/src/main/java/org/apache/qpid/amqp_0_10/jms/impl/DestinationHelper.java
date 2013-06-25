/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.amqp_0_10.jms.impl;

import java.util.List;
import java.util.Map;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQDestination.Binding;
import org.apache.qpid.client.messaging.address.AddressHelper;
import org.apache.qpid.client.messaging.address.Node;
import org.apache.qpid.transport.Option;
import org.apache.qpid.util.Strings;

public class DestinationHelper
{
    public static void handleQueueCreation(SessionImpl ssn, DestinationImpl dest, boolean noLocal) throws JMSException
    {
        Node node = dest.getNode();
        Map<String,Object> arguments = node.getDeclareArgs();
        if (!arguments.containsKey((AddressHelper.NO_LOCAL)))
        {
            arguments.put(AddressHelper.NO_LOCAL, noLocal);
        }
        getQpidSession().queueDeclare(dest.getAddressName(),
                node.getAlternateExchange(), arguments,
                node.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE,
                node.isDurable() ? Option.DURABLE : Option.NONE,
                node.isExclusive() ? Option.EXCLUSIVE : Option.NONE);

        createBindings(dest, dest.getNode().getBindings());
        sync();
    }

    void handleExchangeNodeCreation(AMQDestination dest) throws AMQException
    {
        Node node = dest.getNode();
        sendExchangeDeclare(dest.getAddressName(),
                node.getExchangeType(),
                node.getAlternateExchange(),
                node.getDeclareArgs(),
                false,
                node.isDurable(),
                node.isAutoDelete());

        // If bindings are specified without a queue name and is called by the producer,
        // the broker will send an exception as expected.
        createBindings(dest, dest.getNode().getBindings());
        sync();
    }

    void handleLinkCreation(AMQDestination dest) throws AMQException
    {
        createBindings(dest, dest.getLink().getBindings());
    }

    void createBindings(AMQDestination dest, List<Binding> bindings)
    {
        String defaultExchangeForBinding = dest.getAddressType() == AMQDestination.TOPIC_TYPE ? dest
                .getAddressName() : "amq.topic";

        String defaultQueueName = null;
        if (AMQDestination.QUEUE_TYPE == dest.getAddressType())
        {
            defaultQueueName = dest.getQueueName();
        }
        else
        {
            defaultQueueName = dest.getLink().getName() != null ? dest.getLink().getName() : dest.getQueueName();
        }

        for (Binding binding: bindings)
        {
            String queue = binding.getQueue() == null?
                    defaultQueueName: binding.getQueue();

            String exchange = binding.getExchange() == null ?
                        defaultExchangeForBinding :
                        binding.getExchange();

            if (_logger.isDebugEnabled())
            {
                _logger.debug("Binding queue : " + queue +
                         " exchange: " + exchange +
                         " using binding key " + binding.getBindingKey() +
                         " with args " + Strings.printMap(binding.getArgs()));
            }
            getQpidSession().exchangeBind(queue,
                                     exchange,
                                     binding.getBindingKey(),
                                     binding.getArgs());
       }
    }

    void handleLinkDelete(AMQDestination dest) throws AMQException
    {
        // We need to destroy link bindings
        String defaultExchangeForBinding = dest.getAddressType() == AMQDestination.TOPIC_TYPE ? dest
                .getAddressName() : "amq.topic";

        String defaultQueueName = null;
        if (AMQDestination.QUEUE_TYPE == dest.getAddressType())
        {
            defaultQueueName = dest.getQueueName();
        }
        else
        {
            defaultQueueName = dest.getLink().getName() != null ? dest.getLink().getName() : dest.getQueueName();
        }

        for (Binding binding: dest.getLink().getBindings())
        {
            String queue = binding.getQueue() == null?
                    defaultQueueName: binding.getQueue();

            String exchange = binding.getExchange() == null ?
                        defaultExchangeForBinding :
                        binding.getExchange();

            if (_logger.isDebugEnabled())
            {
                _logger.debug("Unbinding queue : " + queue +
                         " exchange: " + exchange +
                         " using binding key " + binding.getBindingKey() +
                         " with args " + Strings.printMap(binding.getArgs()));
            }
            getQpidSession().exchangeUnbind(queue, exchange,
                                            binding.getBindingKey());
        }
    }

    void deleteSubscriptionQueue(AMQDestination dest) throws AMQException
    {
        // We need to delete the subscription queue.
        if (dest.getAddressType() == AMQDestination.TOPIC_TYPE &&
            dest.getLink().getSubscriptionQueue().isExclusive() &&
            isQueueExist(dest, false))
        {
            getQpidSession().queueDelete(dest.getQueueName());
        }
    }

    void handleNodeDelete(AMQDestination dest) throws AMQException
    {
        if (AMQDestination.TOPIC_TYPE == dest.getAddressType())
        {
            if (isExchangeExist(dest,false))
            {
                getQpidSession().exchangeDelete(dest.getAddressName());
                dest.setAddressResolved(0);
            }
        }
        else
        {
            if (isQueueExist(dest,false))
            {
                getQpidSession().queueDelete(dest.getAddressName());
                dest.setAddressResolved(0);
            }
        }
    }
}
