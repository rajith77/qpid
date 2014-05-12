
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;
import org.apache.qpid.client.AMQConnectionURL;

public class Test
{

    public Test()
    {
        // TODO Auto-generated constructor stub
    }

    public static void basicSendReceive() throws Exception
    {
        String url = "amqp://username:password@clientid/test?brokerlist='tcp://localhost:5672'";
        ConnectionImpl con = new ConnectionImpl(url);

        Session ssn = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        TemporaryQueue queue = ssn.createTemporaryQueue(); // ssn.createQueue("MY_QUEUE;{create : always}");

        //queue.delete();

        MessageProducer prod = ssn.createProducer(queue);
        for (int i = 0; i < 5; i++)
        {
            prod.send(ssn.createTextMessage("Msg" + i));
        }

        con.start();

        MessageConsumer cons = ssn.createConsumer(queue);
        for (int i = 0; i < 5; i++)
        {
            TextMessage msg = (TextMessage) cons.receive();
            System.out.println("Msg Recved : " + msg.getText());
            ssn.recover();
        }

        con.close();
    }

    public static void testRollbackOnClose() throws Exception
    {
        String url = "amqp://username:password@clientid/test?brokerlist='tcp://localhost:5672'";
        ConnectionImpl con = new ConnectionImpl(url);
        con.start();

        Session ssn = con.createSession(true, Session.SESSION_TRANSACTED);
        Destination queue = ssn.createQueue("MY_QUEUE;{create : always}");
        MessageConsumer cons = ssn.createConsumer(queue);
        MessageProducer prod = ssn.createProducer(queue);

        prod.send(ssn.createTextMessage("Msg1"));
        ssn.commit();

        TextMessage msg = (TextMessage) cons.receive();
        System.out.println("Msg Recved : " + msg.getText());

        ssn.close();

        ssn = con.createSession(true, Session.SESSION_TRANSACTED);
        queue = ssn.createQueue("MY_QUEUE;{create : always}");
        cons = ssn.createConsumer(queue);

        msg = (TextMessage) cons.receive();
        System.out.println("Msg Recved : " + msg.getText());
        ssn.rollback();

        msg = (TextMessage) cons.receive();
        System.out.println("Msg Recved : " + msg.getText());

        con.close();
    }

    public static void testExecutionExceptionHandling() throws Exception
    {
        String url = "amqp://username:password@clientid/test?brokerlist='tcp://localhost:5672'";
        ConnectionImpl con = new ConnectionImpl(url);

        Session ssn = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = ssn.createQueue("q; {create: always, node: {x-declare:{arguments:{'qpid.max_count': 2}}}}");

        MessageProducer prod = ssn.createProducer(queue);
        for (int i = 0; i < 5; i++)
        {
            prod.send(ssn.createTextMessage("Msg" + i));
        }
        ssn.close();
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        Test.basicSendReceive();
    }

}
