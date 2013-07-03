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
import java.util.UUID;

import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;

import org.apache.qpid.address.Address;
import org.apache.qpid.address.AddressHelper;
import org.apache.qpid.address.AddressPolicy;
import org.apache.qpid.address.Binding;
import org.apache.qpid.address.Link;
import org.apache.qpid.address.Node;
import org.apache.qpid.address.NodeType;
import org.apache.qpid.address.SubscriptionQueue;
import org.apache.qpid.transport.ExchangeBoundResult;
import org.apache.qpid.transport.ExchangeQueryResult;
import org.apache.qpid.transport.ExecutionErrorCode;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
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

    public static String verifyAndCreateSubscription(SessionImpl ssn, DestinationImpl dest, String consumerId, AcknowledgeMode ackMode, boolean noLocal) throws JMSException
    {
        NodeType nodeType = AddressResolution.resolveDestination(ssn, dest, CheckMode.RECEIVER);
        String subscriptionQueue;
        if (NodeType.TOPIC == nodeType)
        {
            subscriptionQueue = AddressResolution.createSubscriptionQueue(ssn, dest, noLocal);
        }
        else
        {
            subscriptionQueue = dest.getAddress().getName();
        }

        Map<String,Object> args = null;
        if (dest.getAddress().getLink().getSubscription().getArgs().size() > 0)
        {
            args = dest.getAddress().getLink().getSubscription().getArgs();
        }
        
        try
        {
            ssn.getAMQPSession().messageSubscribe(subscriptionQueue, consumerId, getMessageAcceptMode(ackMode),
                    getMessageAcquireMode(dest), null, 0, args,
                    dest.getAddress().getLink().getSubscription().isExclusive() ? Option.EXCLUSIVE : Option.NONE);
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error creating subscription.", e);
        }

        return subscriptionQueue;
    }
    
    static NodeType resolveDestination(SessionImpl ssn, DestinationImpl dest, CheckMode mode)
            throws JMSException
    {
        NodeQueryStatus status = verifyNodeExists(ssn, dest);
        switch (status)
        {
        case QUEUE:
            if (checkAddressPolicy(dest.getAddress().getNode().getAssertPolicy(), mode))
            {
                assertQueue(ssn, dest, mode);
            }
            return NodeType.QUEUE;
        case EXCHANGE:
            if (checkAddressPolicy(dest.getAddress().getNode().getAssertPolicy(), mode))
            {
                assertExchange(ssn, dest, mode);
            }
            return NodeType.TOPIC;
        case NOT_FOUND:
            if (checkAddressPolicy(dest.getAddress().getNode().getCreatePolicy(), mode))
            {
                NodeType type = dest.getAddress().getNode().getType();
                if (type == NodeType.TOPIC)
                {
                    handleExchangeCreation(ssn, dest);
                    return NodeType.TOPIC;
                }
                else
                // if UNDEFINED, still treat it as QUEUE
                {
                    handleQueueCreation(ssn, dest);
                    return NodeType.QUEUE;
                }
            }
            else
            {
                throw new InvalidDestinationException("The name '" + dest.getAddress().getName()
                        + "' supplied in the address doesn't resolve to an exchange or a queue");
            }
        default: // AMBIGUOUS
            throw new InvalidDestinationException("Ambiguous address, please specify node type as 'queue' or 'topic'");
        }
    }

    static void handleQueueCreation(SessionImpl ssn, DestinationImpl dest) throws JMSException
    {
        Address addr = dest.getAddress();
        Node node = addr.getNode();

        try
        {
            ssn.getAMQPSession().queueDeclare(addr.getName(), node.getAlternateExchange(), node.getDeclareArgs(),
                    node.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE,
                    node.isDurable() ? Option.DURABLE : Option.NONE,
                    node.isExclusive() ? Option.EXCLUSIVE : Option.NONE);
        }
        catch (SessionException se)
        {
            _logger.error(se, "Error creating Queue");
            throw ExceptionHelper.toJMSException("Address resolutionn failed!. Error creating Queue", se);
        }

        try
        {
            createBindings(ssn, dest, node.getBindings(), addr.getName(), null);
        }
        catch (SessionException se)
        {
            _logger.error(se, "Error creating node bindings for : " + dest.getAddress());
            throw ExceptionHelper.toJMSException("Address resolutionn failed!. Error creating node bindings for : "
                    + dest.getAddress(), se);
        }
    }

    static void handleExchangeCreation(SessionImpl ssn, DestinationImpl dest) throws JMSException
    {
        Node node = dest.getAddress().getNode();
        String name = dest.getAddress().getName();
        try
        {
            ssn.getAMQPSession().exchangeDeclare(name, node.getExchangeType(), node.getAlternateExchange(),
                    node.getDeclareArgs(), name.toString().startsWith("amq.") ? Option.PASSIVE : Option.NONE,
                    node.isDurable() ? Option.DURABLE : Option.NONE,
                    node.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE);
        }
        catch (SessionException se)
        {
            _logger.error(se, "Error creating Exchange");
            throw ExceptionHelper.toJMSException("Address resolutionn failed!. Error creating Exchange.", se);
        }

        try
        {
            createBindings(ssn, dest, node.getBindings(), name, null);
        }
        catch (SessionException se)
        {
            _logger.error(se, "Error creating node bindings for : " + dest.getAddress());
            throw ExceptionHelper.toJMSException("Address resolutionn failed!. Error creating node bindings for : "
                    + dest.getAddress(), se);
        }
    }

    static void handleLinkCreation(SessionImpl ssn, DestinationImpl dest, String defaultExchange, String defaultQueue)
            throws JMSException
    {
        try
        {
            createBindings(ssn, dest, dest.getAddress().getLink().getBindings(), defaultExchange, defaultQueue);
        }
        catch (SessionException se)
        {
            _logger.error(se, "Error creating link bindings for : " + dest.getAddress());
            ExceptionHelper.toJMSException(
                    "Address resolutionn failed!. Error creating link bindings for : " + dest.getAddress(), se);
        }
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

    static String createSubscriptionQueue(SessionImpl ssn, DestinationImpl dest, boolean noLocal) throws JMSException
    {
        try
        {
            Link link = dest.getAddress().getLink();
            SubscriptionQueue queue = link.getSubscriptionQueue();
            String name = link.getName() == null ? "TempQueue_" + UUID.randomUUID() : dest.getAddress().getLink()
                    .getName();

            Map<String, Object> args = queue.getDeclareArgs();
            if (noLocal)
            {
                args.put(AddressHelper.NO_LOCAL, true);
            }
            
            ssn.getAMQPSession().queueDeclare(name, queue.getAlternateExchange(), args.size() > 0? args : null,
                    queue.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE,
                    link.isDurable() ? Option.DURABLE : Option.NONE,
                    queue.isExclusive() ? Option.EXCLUSIVE : Option.NONE);

            ssn.getAMQPSession().sync();
            return name;
        }
        catch (SessionException se)
        {
            _logger.error(se, "Error creating subscription queue");
            throw ExceptionHelper.toJMSException("Error creating subscription queue", se);
        }
    }

    public static void cleanupForConsumer(SessionImpl ssn, DestinationImpl dest, String subscriptionQueue)
            throws JMSException
    {
        cleanup(ssn, dest, CheckMode.RECEIVER, subscriptionQueue);
    }

    public static void cleanupForProducer(SessionImpl ssn, DestinationImpl dest) throws JMSException
    {
        cleanup(ssn, dest, CheckMode.SENDER, null);
    }

    static void cleanup(SessionImpl ssn, DestinationImpl dest, CheckMode mode, String subscriptionQueue)
            throws JMSException
    {
        try
        {
            NodeType nodeType = null;
            NodeQueryStatus status = verifyNodeExists(ssn, dest);
            switch (status)
            {
            case QUEUE:
                nodeType = NodeType.QUEUE;
                break;
            case EXCHANGE:
                nodeType = NodeType.TOPIC;
                break;
            case AMBIGUOUS:
                switch (dest.getAddress().getNode().getType())
                {
                case QUEUE:
                case TOPIC:
                    nodeType = dest.getAddress().getNode().getType();
                case UNDEFINED:
                    throw new JMSException("Unable to determine node type."
                            + "Ambiguous address, please specify node type as 'queue' or 'topic'");
                }
            case NOT_FOUND:
                break;
            }

            handleLinkDelete(ssn, dest, NodeType.TOPIC == nodeType ? dest.getAddress().getName() : null,
                    subscriptionQueue);

            Link link = dest.getAddress().getLink();
            if (CheckMode.RECEIVER == mode && NodeType.TOPIC == nodeType
                    && (link.getName() == null || link.getSubscriptionQueue().isExclusive()))
            {
                handleQueueDelete(ssn, subscriptionQueue);
            }

            if (checkAddressPolicy(dest.getAddress().getNode().getDeletePolicy(), mode))
            {
                if (NodeType.QUEUE == nodeType)
                {
                    handleQueueDelete(ssn, dest.getAddress().getName());
                }
                else if (NodeType.TOPIC == nodeType)
                {
                    handleExchangeDelete(ssn, dest.getAddress().getName());
                }
                else
                {
                    _logger.warn("Node delete failed as it is already deleted. Address is : " + dest.getAddress());
                    // TODO Should we throw an exception here ?
                }
            }
        }
        catch (SessionException se)
        {
            _logger.error(se, "Error deleting Node for address : " + dest.getAddress());
            throw ExceptionHelper.toJMSException("Error deleting Node for address : " + dest.getAddress(), se);
        }
    }

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

        try
        {
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
        catch (SessionException se)
        {
            _logger.error(se, "Error deleting link bindings for : " + dest.getAddress());
            throw ExceptionHelper.toJMSException("Error deleting link bindings for : " + dest.getAddress(), se);
        }

    }

    static void handleExchangeDelete(SessionImpl ssn, String name) throws JMSException
    {
        try
        {
            ssn.getAMQPSession().exchangeDelete(name);
        }
        catch (Exception se)
        {
            _logger.error(se, "Error deleting Exchange : " + name);
            throw ExceptionHelper.toJMSException("Error deleting Exchange : " + name, se);
        }
    }

    static void handleQueueDelete(SessionImpl ssn, String name) throws JMSException
    {
        try
        {
            ssn.getAMQPSession().queueDelete(name);
        }
        catch (Exception se)
        {
            _logger.error(se, "Error deleting Queue  : " + name);
            throw ExceptionHelper.toJMSException("Error deleting Queue : " + name, se);
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

    static MessageAcceptMode getMessageAcceptMode(AcknowledgeMode mode)
    {
        return mode == AcknowledgeMode.NO_ACK ? MessageAcceptMode.NONE : MessageAcceptMode.EXPLICIT;
    }

    static MessageAcquireMode getMessageAcquireMode(DestinationImpl dest)
    {
        return dest.getAddress().isBrowseOnly() ? MessageAcquireMode.NOT_ACQUIRED : MessageAcquireMode.PRE_ACQUIRED;
    }
}