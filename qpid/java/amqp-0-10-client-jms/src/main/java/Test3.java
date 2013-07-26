

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;

public class Test3
{
    ConnectionImpl _con;
    
    public Test3() throws Exception
    {
        String url = "amqp://username:password@clientid/test?brokerlist='tcp://localhost:5672;tcp://localhost:6672'";
        _con = new ConnectionImpl(url);
        _con.start();
        
        final Session ssn = _con.createSession(true, Session.SESSION_TRANSACTED);
        MessageConsumer cons = ssn.createConsumer(ssn.createQueue("MY_QUEUE;{create: always}"));
        cons.setMessageListener(new MessageListener()
        {

            @Override
            public void onMessage(Message m)
            {
                try
                {
                    System.out.println("Received " + ((TextMessage)m).getText());
                    ssn.commit();
                }
                catch (JMSException e)
                {
                    e.printStackTrace();
                }
            }
            
        });
    }
    
    public void sendMessages()
    {        
        try
        {
            int count = 0;
            Session ssn = _con.createSession(true, Session.SESSION_TRANSACTED);
            MessageProducer prod = ssn.createProducer(ssn.createQueue("MY_QUEUE"));
            while (true)
            {
                prod.send(ssn.createTextMessage("Msg" + count++));
                ssn.commit();
                System.out.println("Sent Msg" + count);
            }
        }
        catch (JMSException e)
        {
           e.printStackTrace();
        }
    }
    

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        final Test3 test = new Test3();
        Thread t = new Thread(new Runnable(){

            @Override
            public void run()
            {
                test.sendMessages();
                
            }});
        t.start();
    }

}
