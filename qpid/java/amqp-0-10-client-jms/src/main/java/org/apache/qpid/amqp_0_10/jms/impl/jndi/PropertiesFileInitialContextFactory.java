/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.amqp_0_10.jms.impl.jndi;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import org.apache.qpid.amqp_0_10.jms.impl.ConnectionFactoryImpl;
import org.apache.qpid.amqp_0_10.jms.impl.DestinationImpl;
import org.apache.qpid.amqp_0_10.jms.impl.QueueImpl;
import org.apache.qpid.amqp_0_10.jms.impl.TopicImpl;
import org.apache.qpid.url.URLSyntaxException;

@SuppressWarnings({"rawtypes", "unchecked"})
public class PropertiesFileInitialContextFactory implements InitialContextFactory
{

    private String CONNECTION_FACTORY_PREFIX = "connectionfactory.";
    private String DESTINATION_PREFIX = "destination.";
    private String QUEUE_PREFIX = "queue.";
    private String TOPIC_PREFIX = "topic.";

    public Context getInitialContext(Hashtable environment) throws NamingException
    {
        Map data = new ConcurrentHashMap();

        String file = null;
        try
        {


            if (environment.containsKey(Context.PROVIDER_URL))
            {
                file = (String) environment.get(Context.PROVIDER_URL);
            }
            else
            {
                file = System.getProperty(Context.PROVIDER_URL);
            }

            if (file != null)
            {

                // Load the properties specified
                BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));
                Properties p = new Properties();
                try
                {
                    p.load(inputStream);
                }
                finally
                {
                    inputStream.close();
                }


                for (Map.Entry me : p.entrySet())
                {
                    String key = (String) me.getKey();
                    String value = (String) me.getValue();
                    environment.put(key, value);
                    if (System.getProperty(key) == null)
                    {
                        System.setProperty(key, value);
                    }
                }
            }
            else
            {
                throw new NamingException("No Provider URL specified.");
            }
        }
        catch (IOException ioe)
        {
            NamingException ne = new NamingException("Unable to load property file:" + file +".");
            ne.setRootCause(ioe);
            throw ne;
        }

        try
        {
            createConnectionFactories(data, environment);
        }
        catch (URLSyntaxException e)
        {
            NamingException ne = new NamingException("Error creating connection factory");
            ne.setRootCause(e);
            throw ne;
        }

        try
        {
            createDestinations(data, environment);

            createQueues(data, environment);

            createTopics(data, environment);
        }
        catch (JMSException e)
        {
            NamingException ne = new NamingException("Error creating destinations");
            ne.setRootCause(e);
            throw ne;
        }

        return createContext(data, environment);
    }

    protected ReadOnlyContext createContext(Map data, Hashtable environment)
    {
        return new ReadOnlyContext(environment, data);
    }

    protected void createConnectionFactories(Map data, Hashtable environment) throws URLSyntaxException
    {
        for (Iterator iter = environment.entrySet().iterator(); iter.hasNext();)
        {
            Map.Entry entry = (Map.Entry) iter.next();
            String key = entry.getKey().toString();
            if (key.startsWith(CONNECTION_FACTORY_PREFIX))
            {
                String jndiName = key.substring(CONNECTION_FACTORY_PREFIX.length());
                ConnectionFactory cf = createFactory(entry.getValue().toString().trim());
                if (cf != null)
                {
                    data.put(jndiName, cf);
                }
            }
        }
    }

    protected void createDestinations(Map data, Hashtable environment) throws JMSException
    {
        for (Iterator iter = environment.entrySet().iterator(); iter.hasNext();)
        {
            Map.Entry entry = (Map.Entry) iter.next();
            String key = entry.getKey().toString();
            if (key.startsWith(DESTINATION_PREFIX))
            {
                String jndiName = key.substring(DESTINATION_PREFIX.length());
                Destination dest = createDestination(entry.getValue().toString().trim());
                if (dest != null)
                {
                    data.put(jndiName, dest);
                }
            }
        }
    }

    protected void createQueues(Map data, Hashtable environment) throws JMSException
    {
        for (Iterator iter = environment.entrySet().iterator(); iter.hasNext();)
        {
            Map.Entry entry = (Map.Entry) iter.next();
            String key = entry.getKey().toString();
            if (key.startsWith(QUEUE_PREFIX))
            {
                String jndiName = key.substring(QUEUE_PREFIX.length());
                Queue q = createQueue(entry.getValue().toString().trim());
                if (q != null)
                {
                    data.put(jndiName, q);
                }
            }
        }
    }

    protected void createTopics(Map data, Hashtable environment) throws JMSException
    {
        for (Iterator iter = environment.entrySet().iterator(); iter.hasNext();)
        {
            Map.Entry entry = (Map.Entry) iter.next();
            String key = entry.getKey().toString();
            if (key.startsWith(TOPIC_PREFIX))
            {
                String jndiName = key.substring(TOPIC_PREFIX.length());
                Topic t = createTopic(entry.getValue().toString().trim());
                if (t != null)
                {
                    data.put(jndiName, t);
                }
            }
        }
    }


    private ConnectionFactory createFactory(String url) throws URLSyntaxException
    {
        return new ConnectionFactoryImpl(url);
    }

    private DestinationImpl createDestination(String str) throws JMSException
    {
        return DestinationImpl.createDestination(str);
    }

    private QueueImpl createQueue(String address) throws JMSException
    {
        return QueueImpl.createQueue(address);
    }

    private TopicImpl createTopic(String address) throws JMSException
    {
        return TopicImpl.createTopic(address);
    }

}
