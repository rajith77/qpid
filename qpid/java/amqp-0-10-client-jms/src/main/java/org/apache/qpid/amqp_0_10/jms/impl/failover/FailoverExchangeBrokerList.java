package org.apache.qpid.amqp_0_10.jms.impl.failover;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.qpid.amqp_0_10.jms.ConnectionListener;
import org.apache.qpid.amqp_0_10.jms.impl.ConnectionImpl;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.util.Logger;

public class FailoverExchangeBrokerList implements BrokerList, ConnectionListener, MessageListener
{
    private static final Logger _logger = Logger.get(URLBrokerList.class);

    private Object _brokerListLock = new Object();

    private final ConnectionImpl _conn;

    private final Broker _initialBroker;

    private final ConnectionSettings _origSettings;

    private final List<Broker> _brokerList = new ArrayList<Broker>();

    private Broker _currentBroker;

    private int _currentBrokerIndex = -1;

    FailoverExchangeBrokerList(ConnectionImpl conn)
    {
        _conn = conn;
        _initialBroker = Broker.getBroker(conn, conn.getConfig().getURL().getBrokerDetails(0));
        _currentBroker = _initialBroker;
        _origSettings = _initialBroker.getSettings();
        _conn.addListener(this);
    }

    @Override
    public Broker getNextBroker()
    {
        synchronized (_brokerListLock)
        {
            if (_brokerList.size() == 0)
            {
                return _initialBroker;
            }
            else if (_brokerList.size() == 1)
            {
                return _brokerList.get(0);
            }
            else
            {
                _currentBrokerIndex++;
                if (_currentBrokerIndex >= _brokerList.size())
                {
                    _currentBrokerIndex = 0;
                }
                Broker broker = _brokerList.get(_currentBrokerIndex);

                if (_currentBroker.getSettings().getHost().equals(broker.getSettings().getHost())
                        && _currentBroker.getSettings().getPort() == broker.getSettings().getPort())
                {
                    return getNextBroker();
                }
                else
                {
                    _currentBroker = broker;
                    return _currentBroker;
                }
            }
        }
    }

    @Override
    public void opened(org.apache.qpid.amqp_0_10.jms.Connection con)
    {
        try
        {
            Session ssn = _conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer cons = ssn.createConsumer(ssn.createTopic("amq.failover"));
            cons.setMessageListener(this);
        }
        catch (JMSException e)
        {
            throw new Error("Error subscribing to the failover_exchange", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onMessage(Message m)
    {
        _logger.warn("Failover exchange notification : cluster membership has changed!");

        String currentBrokerIP = "";
        try
        {
            currentBrokerIP = InetAddress.getByName(_currentBroker.getSettings().getHost()).getHostAddress();
        }
        catch (Exception e)
        {
            _logger.warn("Unable to resolve current broker host name", e);
        }

        try
        {
            synchronized (_brokerListLock)
            {
                _brokerList.clear();
                List<String> list = (List<String>) m.getObjectProperty("amq.failover");
                for (String brokerEntry : list)
                {
                    String[] urls = brokerEntry.substring(5).split(",");

                    for (String url : urls)
                    {
                        String[] tokens = url.split(":");
                        if (tokens[0].equalsIgnoreCase(_origSettings.getProtocol()))
                        {
                            ConnectionSettingsImpl settings = (ConnectionSettingsImpl) _origSettings.copy();
                            Broker broker = new Broker(settings, _initialBroker.getRetries(),
                                    _initialBroker.getConnectDelay());
                            settings.setHost(tokens[1]);
                            settings.setPort(Integer.parseInt(tokens[2]));
                            _brokerList.add(broker);

                            if (currentBrokerIP.equals(settings.getHost())
                                    && _currentBroker.getSettings().getPort() == settings.getPort())
                            {
                                _currentBrokerIndex = _brokerList.indexOf(broker);
                            }

                            break;
                        }
                    }
                }
                _logger.warn("Updated broker list : " + _brokerList);
            }
        }
        catch (JMSException e)
        {
            _logger.error("Error parsing the message sent by failover exchange", e);
        }
    }

    @Override
    public void started(org.apache.qpid.amqp_0_10.jms.Connection con)
    {
    }

    @Override
    public void stopped(org.apache.qpid.amqp_0_10.jms.Connection con)
    {
    }

    @Override
    public void closed(org.apache.qpid.amqp_0_10.jms.Connection con)
    {
    }

    @Override
    public void exception(org.apache.qpid.amqp_0_10.jms.Connection con, Exception exp)
    {
    }

    @Override
    public void protocolConnectionCreated(org.apache.qpid.amqp_0_10.jms.Connection con)
    {
    }

    @Override
    public void protocolConnectionLost(org.apache.qpid.amqp_0_10.jms.Connection con)
    {
    }

    @Override
    public void preFailover(org.apache.qpid.amqp_0_10.jms.Connection con)
    {
    }

    @Override
    public void postFailover(org.apache.qpid.amqp_0_10.jms.Connection con)
    {
    }
}