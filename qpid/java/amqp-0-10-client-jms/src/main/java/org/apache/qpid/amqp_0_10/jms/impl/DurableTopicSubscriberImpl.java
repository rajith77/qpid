package org.apache.qpid.amqp_0_10.jms.impl;

import javax.jms.JMSException;

import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.ExceptionHelper;

public class DurableTopicSubscriberImpl extends TopicSubscriberImpl
{
    private static final Logger _logger = Logger.get(DurableTopicSubscriberImpl.class);

    private final String _subscriberName;

    public DurableTopicSubscriberImpl(String subscriberName, String consumerId, SessionImpl ssn, TopicImpl topic,
            String selector, boolean noLocal, boolean browseOnly, AcknowledgeMode ackMode) throws JMSException
    {
        super(consumerId, ssn, topic, selector, noLocal, browseOnly, ackMode, getSubscriptionQueueName(ssn,
                subscriberName));
        _subscriberName = subscriberName;
    }

    @Override
    void createSubscription() throws JMSException
    {
        try
        {
            AddressResolution.verifyAndCreateDurableTopicSubscription(this);
            setMessageFlowMode();
            getSession().getAMQPSession().sync();
        }
        catch (Exception se)
        {
            throw ExceptionHelper.toJMSException("Error creating durable subscription for Topic : [" + getDestination()
                    + "]", se);
        }
        _logger.debug("Sucessfully created durable topic subscriber for : " + getTopic());
    }

    @Override
    protected void closeConsumer() throws JMSException
    {
        cancelSubscription();
        releaseMessages();
        getSession().getAMQPSession().sync();
    }
    
    public String getSubscriberName()
    {
        return _subscriberName;
    }

    private static String getSubscriptionQueueName(SessionImpl ssn, String subscriberName) throws JMSException
    {
        return ssn.getConnection().getClientID() + ":" + subscriberName;
    }
}