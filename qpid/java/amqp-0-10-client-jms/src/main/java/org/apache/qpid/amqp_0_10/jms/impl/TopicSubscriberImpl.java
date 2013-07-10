package org.apache.qpid.amqp_0_10.jms.impl;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

public class TopicSubscriberImpl extends MessageConsumerImpl implements TopicSubscriber
{

    public TopicSubscriberImpl(String consumerTag, SessionImpl ssn, Destination dest, String selector, boolean noLocal,
            boolean browseOnly, AcknowledgeMode ackMode) throws JMSException
    {
        super(consumerTag, ssn, dest, selector, noLocal, browseOnly, ackMode);
        if (des)
    }

    @Override
    public Topic getTopic() throws JMSException
    {
        return (TopicImpl)getDestination();
    }

}
