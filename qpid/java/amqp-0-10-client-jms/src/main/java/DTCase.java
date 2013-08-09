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

public class DTCase
{
    final ConnectionImpl _con;

    public DTCase() throws Exception
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
                    final MessageProducer prod = ssn.createProducer(ssn.createQueue("DT_TEST_DESTINATION"));
                    MessageListener l = new MessageListener()
                    {

                        @Override
                        public void onMessage(Message m)
                        {
                            try
                            {
                                count++;
                                if (count % 1000 == 0)
                                {
                                    System.out.println("Consumer : " + id + " Received " + ((TextMessage) m).getText());
                                }
                                m.clearProperties();
                                prod.send(m);
                                ssn.commit();
                            }
                            catch (JMSException e)
                            {
                                e.printStackTrace();
                            }
                        }

                    };
                    MessageConsumer cons1 = ssn.createConsumer(ssn.createQueue("DT_TEST"));
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

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        final DTCase test = new DTCase();
        for (int i = 0; i < 4; i++)
        {
            test.createConsumer(i);
        }
    }
}
