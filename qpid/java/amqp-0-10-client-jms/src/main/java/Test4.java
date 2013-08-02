

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

public class Test4
{
    ConnectionImpl _con;
    AtomicBoolean _continue = new AtomicBoolean(true);
    int _count = 0;
    
    public Test4() throws Exception
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
        
        sendMessages();        
        Thread.sleep(5000);
        
        final Session ssn = _con.createSession(true, Session.SESSION_TRANSACTED);
        
        MessageConsumer cons = ssn.createConsumer(ssn.createQueue("MY_QUEUE"));
        cons.setMessageListener(new MessageListener()
        {

            @Override
            public void onMessage(Message m)
            {
                try
                {
                    _count++;
                    System.out.println("Received " + ((TextMessage)m).getText());
                    if (_count == 5)
                    {
                        ssn.rollback();
                    }
                    else
                    {
                        ssn.commit();
                    }
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
            Session ssn = _con.createSession(true, Session.SESSION_TRANSACTED);
            MessageProducer prod = ssn.createProducer(ssn.createQueue("MY_QUEUE;{create: always}"));
            for (int i = 0; i < 10; i++)
            {
                try
                {
                    prod.send(ssn.createTextMessage("Msg" + i));
                    ssn.commit();
                    System.out.println("Sent Msg" + i);
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
        
        final Test4 test = new Test4();
        Thread t = new Thread(new Runnable(){

            @Override
            public void run()
            {
                test.sendMessages();
                
            }});
        t.start();
    }

}
