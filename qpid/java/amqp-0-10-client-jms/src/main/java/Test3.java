

import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;

public class Test3
{
    ConnectionImpl _con;
    AtomicBoolean _continue = new AtomicBoolean(true);
    
    public Test3() throws Exception
    {
        String url = "amqp://username:password@clientid/test?failover='roundrobin?cyclecount='0''&brokerlist='tcp://localhost:5672;tcp://localhost:6672'";
        _con = new ConnectionImpl(url);
        _con.setExceptionListener(new ExceptionListener(){

            @Override
            public void onException(JMSException arg0)
            {
                _continue.set(false);
                
            }});
        _con.start();
        
        
        final Session ssn = _con.createSession(true, Session.SESSION_TRANSACTED);
        
        Topic topic = ssn.createTopic("Hello");
        
        MessageConsumer cons = ssn.createConsumer(ssn.createQueue("MY_QUEUE"));
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
            MessageProducer prod = ssn.createProducer(ssn.createQueue("MY_QUEUE;{create: always}"));
            while (_continue.get())
            {
                try
                {
                    prod.send(ssn.createTextMessage("Msg" + count));
                    ssn.commit();
                    System.out.println("Sent Msg" + count);
                    count++;
                }
                catch (JMSException e)
                {
                    e.printStackTrace();
                }
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
        Connection con = JMSProvider.getConnection("amqp://username:password@clientid/test?failover='roundrobin?cyclecount='0''&brokerlist='tcp://localhost:5672;tcp://localhost:6672'");
        
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
