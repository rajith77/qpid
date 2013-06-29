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

import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;

import org.apache.qpid.AMQException;
import org.apache.qpid.address.Address;
import org.apache.qpid.address.AddressHelper;
import org.apache.qpid.address.AddressPolicy;
import org.apache.qpid.address.Binding;
import org.apache.qpid.address.Link;
import org.apache.qpid.address.Node;
import org.apache.qpid.address.NodeType;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.transport.ExchangeBoundResult;
import org.apache.qpid.transport.ExchangeQueryResult;
import org.apache.qpid.transport.ExecutionErrorCode;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.QueueQueryResult;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.ExceptionHelper;
import org.apache.qpid.util.Strings;

public class AddressResolution
{
    private static final Logger _logger = Logger.get(AddressResolution.class);

    public enum CheckMode
    {
        RECEIVER, SENDER
    };

    enum NodeQueryStatus
    {
        QUEUE, EXCHANGE, AMBIGUOUS, NOT_FOUND
    };

    static void verifyDestination(SessionImpl ssn, DestinationImpl dest, CheckMode mode) throws JMSException
    {
        NodeQueryStatus status = verifyNodeExists(ssn, dest);
        switch (status)
        {
        case QUEUE:
            if (checkAddressPolicy(dest.getAddress().getNode().getAssertPolicy(), mode))
            {
                assertQueue(ssn, dest, mode);
            }
            break;
        case EXCHANGE:
            if (checkAddressPolicy(dest.getAddress().getNode().getAssertPolicy(), mode))
            {
                assertExchange(ssn, dest, mode);
            }
            break;
        case NOT_FOUND:
            if (checkAddressPolicy(dest.getAddress().getNode().getCreatePolicy(), mode))
            {
                NodeType type = dest.getAddress().getNode().getType();
                if (type == NodeType.TOPIC)
                {
                    handleExchangeCreation(ssn, dest);
                }
                else
                // if UNDEFINED, still treat it as QUEUE
                {
                    handleQueueCreation(ssn, dest);
                }
            }
            else
            {
                throw new InvalidDestinationException("The name '" + dest.getAddress().getName()
                        + "' supplied in the address doesn't resolve to an exchange or a queue");
            }
        case AMBIGUOUS:
            throw new InvalidDestinationException("Ambiguous address, please specify node type as 'queue' or 'topic'");
        }
    }

    static void handleQueueCreation(SessionImpl ssn, DestinationImpl dest) throws JMSException
    {
        Address addr = dest.getAddress();
        Node node = addr.getNode();

        ssn.getAMQPSession().queueDeclare(addr.getName(), node.getAlternateExchange(), node.getDeclareArgs(),
                node.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE,
                node.isDurable() ? Option.DURABLE : Option.NONE, node.isExclusive() ? Option.EXCLUSIVE : Option.NONE);

        createBindings(ssn, dest, node.getBindings(), addr.getName(), null);
        // sync();
    }

    static void handleExchangeCreation(SessionImpl ssn, DestinationImpl dest) throws JMSException
    {
        Node node = dest.getAddress().getNode();
        String name = dest.getAddress().getName();
        ssn.getAMQPSession()
                .exchangeDeclare(name, node.getExchangeType(), node.getAlternateExchange(), node.getDeclareArgs(),
                        name.toString().startsWith("amq.") ? Option.PASSIVE : Option.NONE,
                        node.isDurable() ? Option.DURABLE : Option.NONE,
                        node.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE);

        createBindings(ssn, dest, node.getBindings(), name, null);
        // sync();
    }

    static void handleLinkCreation(SessionImpl ssn, DestinationImpl dest, String defaultExchange, String defaultQueue)
            throws JMSException
    {
        createBindings(ssn, dest, dest.getAddress().getLink().getBindings(), defaultExchange, defaultQueue);
    }

    static void createBindings(SessionImpl ssn, DestinationImpl dest, List<Binding> bindings, String defaultExchange,
            String defaultQueue) throws JMSException
    {
        // Verify that all bindings have the exchange specified.
        if (defaultExchange == null)
        {
            for (Binding binding : bindings)
            {
                if (binding.getExchange() == null || binding.getExchange().trim().isEmpty())
                {
                    throw new JMSException(
                            "Exchange is not specified in bindings, and cannot be inferred from the address strings");
                }
            }
        }

        // Verify that all bindings have the queue specified.
        if (defaultQueue == null)
        {
            for (Binding binding : bindings)
            {
                if (binding.getQueue() == null || binding.getQueue().trim().isEmpty())
                {
                    throw new JMSException(
                            "Queue is not specified in bindings, and cannot be inferred from the address strings");
                }
            }
        }

        for (Binding binding : bindings)
        {
            String queue = binding.getQueue() == null ? defaultQueue : binding.getQueue();

            String exchange = binding.getExchange() == null ? defaultExchange : binding.getExchange();

            if (_logger.isDebugEnabled())
            {
                _logger.debug("Binding queue : " + queue + " exchange: " + exchange + " using binding key "
                        + binding.getBindingKey() + " with args " + Strings.printMap(binding.getArgs()));
            }
            ssn.getAMQPSession().exchangeBind(queue, exchange, binding.getBindingKey(), binding.getArgs());
        }
    }

    // We need to destroy link bindings
    static void handleLinkDelete(SessionImpl ssn, DestinationImpl dest, String defaultExchange, String defaultQueue)
            throws JMSException
    {
        List<Binding> bindings = dest.getAddress().getLink().getBindings();

        // Verify that all bindings have the exchange specified.
        if (defaultExchange == null)
        {
            for (Binding binding : bindings)
            {
                if (binding.getExchange() == null || binding.getExchange().trim().isEmpty())
                {
                    throw new JMSException(
                            "Exchange is not specified in bindings, and cannot be inferred from the address strings");
                }
            }
        }

        // Verify that all bindings have the queue specified.
        if (defaultQueue == null)
        {
            for (Binding binding : bindings)
            {
                if (binding.getQueue() == null || binding.getQueue().trim().isEmpty())
                {
                    throw new JMSException(
                            "Queue is not specified in bindings, and cannot be inferred from the address strings");
                }
            }
        }

        for (Binding binding : bindings)
        {
            String queue = binding.getQueue() == null ? defaultQueue : binding.getQueue();

            String exchange = binding.getExchange() == null ? defaultExchange : binding.getExchange();

            if (_logger.isDebugEnabled())
            {
                _logger.debug("Unbinding queue : " + queue + " exchange: " + exchange + " using binding key "
                        + binding.getBindingKey() + " with args " + Strings.printMap(binding.getArgs()));
            }
            ssn.getAMQPSession().exchangeUnbind(queue, exchange, binding.getBindingKey());
        }
    }

    // TODO handle deletion of subscription queue

    static void handleNodeDelete(SessionImpl ssn, DestinationImpl dest, CheckMode mode) throws JMSException
    {
        if (checkAddressPolicy(dest.getAddress().getNode().getDeletePolicy(), mode))
        {
            NodeQueryStatus status = verifyNodeExists(ssn, dest);
            switch (status)
            {
            case QUEUE:
                ssn.getAMQPSession().queueDelete(dest.getAddress().getName());
                break;
            case EXCHANGE:
                ssn.getAMQPSession().exchangeDelete(dest.getAddress().getName());
                break;
            case AMBIGUOUS:
                switch (dest.getAddress().getNode().getType())
                {
                case QUEUE:
                    ssn.getAMQPSession().queueDelete(dest.getAddress().getName());
                case TOPIC:
                    ssn.getAMQPSession().exchangeDelete(dest.getAddress().getName());
                case UNDEFINED:
                    throw new JMSException("Unable to delete node."
                            + "Ambiguous address, please specify node type as 'queue' or 'topic'");
                }
            default:
                break;
            }
        }
    }

    static NodeQueryStatus verifyNodeExists(SessionImpl ssn, DestinationImpl dest)
    {
        String name = dest.getAddress().getName();
        ExchangeBoundResult result = ssn.getAMQPSession().exchangeBound(name, name, null, null).get();
        if (result.getQueueNotFound() && result.getExchangeNotFound())
        {
            // neither a queue nor an exchange exists with that name
            return NodeQueryStatus.NOT_FOUND;
        }
        else if (result.getExchangeNotFound())
        {
            // name refers to a queue
            return NodeQueryStatus.QUEUE;
        }
        else if (result.getQueueNotFound())
        {
            // name refers to an exchange
            return NodeQueryStatus.EXCHANGE;
        }
        else
        {
            return NodeQueryStatus.AMBIGUOUS;
        }
    }

    static boolean checkAddressPolicy(AddressPolicy policy, CheckMode mode)
    {
        return policy == AddressPolicy.ALWAYS || (mode == CheckMode.RECEIVER && policy == AddressPolicy.RECEIVER)
                || (mode == CheckMode.SENDER && policy == AddressPolicy.SENDER);
    }

    static void assertQueue(SessionImpl ssn, DestinationImpl dest, CheckMode mode) throws JMSException
    {
        try
        {
            boolean match = false;
            Node node = dest.getAddress().getNode();
            QueueQueryResult result = ssn.getAMQPSession().queueQuery(node.getName(), Option.NONE).get();
            match = node.getName().equals(result.getQueue());

            if (match)
            {
                match = (result.getDurable() == node.isDurable()) && (result.getAutoDelete() == node.isAutoDelete())
                        && (result.getExclusive() == node.isExclusive())
                        && (matchProps(result.getArguments(), node.getDeclareArgs()));
            }
            if (!match)
            {
                throw new InvalidDestinationException("Assert failed for address : [" + dest.getAddress()
                        + "], QueueQueryResult was : [" + result + "]");
            }
        }
        catch (SessionException se)
        {
            if (se.getException() != null && se.getException().getErrorCode() == ExecutionErrorCode.RESOURCE_DELETED)
            {
                if (!checkAddressPolicy(dest.getAddress().getNode().getCreatePolicy(), mode))
                {
                    throw new InvalidDestinationException("Assert failed. The Queue has been deleted");
                }
            }
            else
            {
                throw ExceptionHelper.toJMSException("Assert failed due to exception", se);
            }
        }
    }

    static void assertExchange(SessionImpl ssn, DestinationImpl dest, CheckMode mode) throws JMSException
    {
        try
        {
            boolean match = false;
            Node node = dest.getAddress().getNode();
            ExchangeQueryResult result = ssn.getAMQPSession().exchangeQuery(node.getName(), Option.NONE).get();
            match = !result.getNotFound();

            if (match)
            {
                match = (result.getDurable() == node.isDurable())
                        && (node.getExchangeType() != null && node.getExchangeType().equals(result.getType()))
                        && (matchProps(result.getArguments(), node.getDeclareArgs()));
            }
            if (!match)
            {
                throw new InvalidDestinationException("Assert failed for address : [" + dest.getAddress()
                        + "], ExchangeQueryResult was : [" + result + "]");
            }
        }
        catch (SessionException se)
        {
            if (se.getException() != null && se.getException().getErrorCode() == ExecutionErrorCode.RESOURCE_DELETED)
            {
                if (!checkAddressPolicy(dest.getAddress().getNode().getCreatePolicy(), mode))
                {
                    throw new InvalidDestinationException("Assert failed. The Exchange has been deleted");
                }
            }
            else
            {
                throw ExceptionHelper.toJMSException("Assert failed due to exception", se);
            }
        }
    }

    static int evaluateCapacity(int defaultCapacity, DestinationImpl dest)
    {
        Link link = dest.getAddress().getLink();
        if (link.getConsumerCapacity() > 0)
        {
            return link.getConsumerCapacity();
        }
        else
        {
            return defaultCapacity;
        }        
    }

    static boolean matchProps(Map<String, Object> target, Map<String, Object> source)
    {
        boolean match = true;
        for (String key : source.keySet())
        {
            match = target.containsKey(key) && target.get(key).equals(source.get(key));

            if (!match)
            {
                StringBuffer buf = new StringBuffer();
                buf.append("Property given in address did not match with the args sent by the broker.");
                buf.append(" Expected { ").append(key).append(" : ").append(source.get(key)).append(" }, ");
                buf.append(" Actual { ").append(key).append(" : ").append(target.get(key)).append(" }");
                _logger.debug(buf.toString());
                return match;
            }
        }
        return match;
    }
}