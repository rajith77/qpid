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

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;

public class Test4
{
    final ConnectionImpl _con;

    AtomicBoolean _continue = new AtomicBoolean(true);

    public Test4() throws Exception
    {
        String url = "amqp://username:password@clientid/test?failover='roundrobin?cyclecount='4''&brokerlist='tcp://localhost:5672;tcp://localhost:6672'";
        _con = new ConnectionImpl(url);
        _con.setExceptionListener(new ExceptionListener()
        {

            @Override
            public void onException(JMSException e)
            {
                System.out.println("We got a connection exception!!!!!!");
                e.printStackTrace();
                _continue.set(false);

            }
        });
        _con.start();
    }

    public void createConsumer(final int id)
    {
        Runnable r = new Runnable()
        {
            int count = 0;
            
            public void run()
            {
                try
                {
                    final Session ssn = _con.createSession(true, Session.SESSION_TRANSACTED);
                    MessageListener l = new MessageListener()
                    {

                        @Override
                        public void onMessage(Message m)
                        {
                            try
                            {
                                count++;
                                
                                if (count % 200 == 0)
                                {
                                    System.out.println("Consumer : " + id + " Received " + ((TextMessage) m).getText());
                                }
                                /*
                                 * if (_count >= 5) { ssn.rollback(); } else {
                                 * ssn.commit(); }
                                 */
                                ssn.commit();
                            }
                            catch (JMSException e)
                            {
                                e.printStackTrace();
                            }
                        }

                    };
                    MessageConsumer cons1 = ssn.createConsumer(ssn.createQueue("MY_QUEUE;{create: always}"));
                    cons1.setMessageListener(l);

                    final Object o = new Object();
                    synchronized (o)
                    {
                        try
                        {
                            o.wait();
                        }
                        catch (InterruptedException e)
                        {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
                catch (Exception e)
                {
                    // TODO Auto-generated catch block
                    System.out.println("Exception in receiver!!!!!!!");
                    e.printStackTrace();
                    return;
                }
            }
        };

        Thread t = new Thread(r);
        t.setName("Thread-" + id);
        t.start();
    }

    public void sendMessages() throws Exception
    {
        Session ssn = _con.createSession(true, Session.SESSION_TRANSACTED);
        // MessageProducer prod =
        // ssn.createProducer(ssn.createQueue("MY_QUEUE;{create: always, node:{x-declare:{arguments:{'qpid.max_count': 2}}}}"));
        MessageProducer prod = ssn.createProducer(ssn.createQueue("MY_QUEUE;{create: always}"));
        // for (int i = 0; i < 10; i++)
        int i = 0;
        while (true)
        {
            try
            {
                prod.send(ssn.createTextMessage("Msg" + i));
                ssn.commit();
                if (i % 200 == 0)
                {
                    System.out.println("Sent Msg" + i);
                }
                i++;
            }
            catch (JMSException e)
            {
                if (e.getMessage().contains("closed"))
                {
                    throw e;
                }
            }
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        final Test4 test = new Test4();
        for (int i = 0; i < 4; i++)
        {
            test.createConsumer(i);
        }
        Thread t = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                try
                {
                    test.sendMessages();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    return;
                }

            }
        });
        t.start();
    }
}
