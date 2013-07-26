

import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;
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
        String url = "amqp://username:password@clientid/test?brokerlist='tcp://localhost:5672'";
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
        synchronized (o)
        {
            o.wait();
        }
    }

}
