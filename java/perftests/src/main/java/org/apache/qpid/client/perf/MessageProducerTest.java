package org.apache.qpid.client.perf;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.message.TestMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageProducerTest extends Options
{
    private static final Logger _logger = LoggerFactory.getLogger(MessageProducerTest.class);
    private SimpleDateFormat df = new SimpleDateFormat("h:mm a");

    String _logFileName;
    long _startTime;
    long _totalMsgCount;
    long _intervalCount;

    private Connection _connection;
    private Session _session;
    private BytesMessage _payload;
    private MessageProducer _producer;

    public void init() throws Exception
    {
       this.parseOptions();
       _logFileName = _logFilePath + "/MessageProducerTest_" + System.currentTimeMillis();

       AMQConnection _connection = ConnectionUtility.getInstance().getConnection();
       _connection.start();
       Destination dest = Boolean.getBoolean("useQueue")? new AMQQueue(_connection,_destination) : new AMQTopic(_connection,_destination);
       _session = _connection.createSession(_transacted, Session.AUTO_ACKNOWLEDGE);
       _payload = TestMessageFactory.newBytesMessage(_session, _messageSize);
       _producer = _session.createProducer(dest);
       // this should speedup the message producer
       _producer.setDisableMessageTimestamp(true);
    }

    public void run()
    {
        _startTime = System.currentTimeMillis();
        boolean run = true;
        if(Boolean.getBoolean("collect_stats"))
        {
            printHeading();
            runReaper();
        }

        try
        {
            while (run)
            {
                _payload.setJMSMessageID(String.valueOf(_totalMsgCount+1));
                _producer.send(_payload);
                _totalMsgCount ++;
                _intervalCount ++;

                // check every x messages to see if times up
                if(_intervalCount >= _logFrequency)
                {
                    _intervalCount = 0;
                    if (Boolean.getBoolean("collect_stats"))
                    {
                        runReaper();
                    }
                    if (System.currentTimeMillis() - _startTime >= _expiry)
                    {
                        // time to stop the test.
                        _session.close();
                        _connection.stop();
                        run = false;
                    }
                }
            }
        }
        catch (Exception e)
        {
            _logger.error("The timer thread exited", e);
        }
        printSummary();
    }

    public void runReaper()
    {
        try
        {
            FileWriter _memoryLog = new FileWriter(_logFileName + ".csv",true);
            StringBuffer buf = new StringBuffer();
            Date d = new Date(System.currentTimeMillis());
            double totaltime = d.getTime() - _startTime;
            buf.append(df.format(d)).append(",");
            buf.append(d.getTime()).append(",");
            buf.append(_totalMsgCount).append(",");
            buf.append(_totalMsgCount*1000 /totaltime).append(",");
            buf.append(Runtime.getRuntime().totalMemory() -Runtime.getRuntime().freeMemory()).append("\n");
            buf.append("\n");
            _memoryLog.write(buf.toString());
            _memoryLog.close();
            System.out.println(buf);
        }
        catch (Exception e)
        {
            _logger.error("Error printing info to the log file", e);
        }
    }

    private void printHeading()
    {
        try
        {
            FileWriter _memoryLog = new FileWriter(_logFileName + ".csv",true);
            String s = "Date/Time,Time (ms),total msg count,total rate (msg/sec),memory";
            _memoryLog.write(s);
            _memoryLog.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void printSummary()
    {
        try
        {

            long current = System.currentTimeMillis();
            double time = current - _startTime;
            double ratio = _totalMsgCount*1000/time;
            FileWriter _summaryLog = new FileWriter(_logFileName + "_Summary",true);

            StringBuffer buf = new StringBuffer("MessageProducerTest \n Test started at : ");
            buf.append(df.format(new Date(_startTime))).append("\n Test finished at : ");
            Date d = new Date(current);
            buf.append(df.format(d)).append("\n Total Time taken (ms):");
            buf.append(time).append("\n Total messages sent:");
            buf.append(_totalMsgCount).append("\n producer rate:");
            buf.append(ratio).append("\n");
            _summaryLog.write(buf.toString());
            System.out.println("---------- Test Ended -------------");
            _summaryLog.close();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        try
        {
            MessageProducerTest test = new MessageProducerTest();
            test.init();
            test.run();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

}
