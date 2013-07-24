package org.apache.qpid.amqp_0_10.jms.impl.failover;

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;

public class FailoverExchangeBrokerList implements BrokerList
{
    FailoverExchangeBrokerList (ConnectionImpl conn)
    {
        
    }

    @Override
    public Broker getNextBroker()
    {
        // TODO Auto-generated method stub
        return null;
    }

}
