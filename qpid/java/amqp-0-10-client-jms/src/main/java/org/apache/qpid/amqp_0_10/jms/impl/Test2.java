package org.apache.qpid.amqp_0_10.jms.impl;

import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnectionURL;

public class Test2
{

    public Test2()
    {
        
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        AMQConnectionURL url = new AMQConnectionURL("amqp://guest:guest@test/?failover='roundrobin?cyclecount='1000''&brokerlist='tcp://localhost:5001?retries='10'&connectdelay='1000'&connecttimeout='1000000';tcp://localhost:5002?retries='10'&connectdelay='1'&connecttimeout='1';tcp://localhost:5003?retries='10'&connectdelay='1'&connecttimeout='1''");
        ConnectionImpl con = new ConnectionImpl(url);
        
        Session ssn = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination queue = ssn.createTemporaryTopic(); //ssn.createQueue("MY_QUEUE;{create : always}");
        
        con.start();
        
        MessageConsumer cons = ssn.createConsumer(queue);
        cons.setMessageListener(new MessageListener()
        {

            @Override
            public void onMessage(Message m)
            {
                
            }
            
        });
        
        Object o = new Object();
        o.wait();
    }

}
