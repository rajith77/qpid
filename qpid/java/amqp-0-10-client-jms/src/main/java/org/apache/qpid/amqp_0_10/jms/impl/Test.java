package org.apache.qpid.amqp_0_10.jms.impl;

import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnectionURL;

public class Test
{

    public Test()
    {
        // TODO Auto-generated constructor stub
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        AMQConnectionURL url = new AMQConnectionURL("amqp://username:password@clientid/test?brokerlist='tcp://localhost:5672'");
        ConnectionImpl con = new ConnectionImpl(url);
        
        Session ssn = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = ssn.createQueue("hello;{create : always}");
        
        MessageProducer prod = ssn.createProducer(queue);
        for (int i=0; i < 5; i++)
        {
            prod.send(ssn.createTextMessage("Msg" + i));
        }
        
        con.start();
        
        MessageConsumer cons = ssn.createConsumer(queue);
        for (int i=0; i < 5; i++)
        {
            System.out.println("Msg Recved : " + cons.receive());
            ssn.recover();
        }

        con.close();
    }

}
