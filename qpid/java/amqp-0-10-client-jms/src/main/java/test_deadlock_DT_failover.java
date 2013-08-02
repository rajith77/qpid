import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;

public class test_deadlock_DT_failover
{

    public static void main(String[] args)
    {
        test_deadlock_DT_failover testMsg = new test_deadlock_DT_failover();
    }

    private MessageConsumer clientConsumer;

    private MessageProducer clientProducer;

    private Session session;

    private Connection connection;

    final MessageListener messageListener = new MessageListener()
    {
        @Override
        public void onMessage(Message _message)
        {
            try
            {
                String text = ((TextMessage) _message).getText();
                int nr = Integer.parseInt(text);
                nr = nr + 1;
                System.out.println(nr + "th message received..");
                Message msg = session.createTextMessage("" + nr);
                clientProducer.send(msg);
                session.commit();
                System.out.println("..and commited");
            }
            catch (Exception e)
            {
                e.printStackTrace();
                try
                {
                    System.out.println("rolling back");
                    MessageProducer myClientProducer = session.createProducer(session.createQueue(
                            "FailedQueue;{create:always}"));
                    myClientProducer.send(_message);
                    // session.rollback();
                }
                catch (Exception e2)
                {
                    try
                    {
                        System.out.println("rollback failed");
                        e2.printStackTrace();
                        // connection.close();
                        connection.start();
                        // session.close();
                        session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
                        Destination fromDest = session.createQueue("FromQueue;{create:always}");
                        Destination toDest = session.createQueue("ToQueue;{create:always}");
                        clientConsumer = session.createConsumer(fromDest);
                        clientProducer = session.createProducer(toDest);
                        clientConsumer.setMessageListener(messageListener);
                        clientProducer.send(session.createTextMessage("0"));
                        session.commit();
                    }
                    catch (Exception e3)
                    {
                        System.out.println("recovery failed");
                        e3.printStackTrace();
                    }
                }
            }
        }
    };

    public test_deadlock_DT_failover()
    {
        try
        {
            connection = new ConnectionImpl(
                    "amqp://anonymous:anonymous@clientid/test?brokerlist='tcp://localhost:5672?sasl_mechs='ANONYMOUS''&failover='failover_exchange'");
            connection.start();
            session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
            Destination fromDest = session.createQueue("FromQueue;{create:always}");
            Destination toDest = session.createQueue("ToQueue;{create:always}");
            clientConsumer = session.createConsumer(fromDest);
            clientProducer = session.createProducer(toDest);
            MessageProducer initProducer = session.createProducer(fromDest);
            MessageConsumer clientConsumer2 = session.createConsumer(fromDest);
            MessageConsumer clientConsumer3 = session.createConsumer(fromDest);
            clientConsumer.setMessageListener(messageListener);
            clientConsumer2.setMessageListener(messageListener);
            clientConsumer3.setMessageListener(messageListener);

            for (int i = 0; i < 10000; i++)
                initProducer.send(session.createTextMessage(String.valueOf(i)));
            session.commit();

            Thread.sleep(100000);

            clientConsumer.close();
            clientProducer.close();
            session.close();
            connection.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        System.out.println("Koncim.");
    }
}