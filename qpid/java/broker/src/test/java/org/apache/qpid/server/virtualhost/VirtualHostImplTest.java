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
package org.apache.qpid.server.virtualhost;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration.ConfigurationException;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.startup.VirtualHostRecoverer;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VirtualHostImplTest extends QpidTestCase
{
    private VirtualHostRegistry _virtualHostRegistry;

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            if (_virtualHostRegistry != null)
            {
                _virtualHostRegistry.close();
            }
        }

    }

    /**
     * Tests that custom routing keys for the queue specified in the configuration
     * file are correctly bound to the exchange (in addition to the queue name)
     */
    public void testSpecifyingCustomBindings() throws Exception
    {
        customBindingTestImpl(new String[]{"custom1","custom2"});
    }

    /**
     * Tests that a queue specified in the configuration file to be bound to a
     * specified(non-default) direct exchange is a correctly bound to the exchange
     * and the default exchange using the queue name.
     */
    public void testQueueSpecifiedInConfigurationIsBoundToDefaultExchange() throws Exception
    {
        customBindingTestImpl(new String[0]);
    }

    /**
     * Tests that specifying custom routing keys for a queue in the configuration file results in failure
     * to create the vhost (since this is illegal, only queue names are used with the default exchange)
     */
    public void testSpecifyingCustomBindingForDefaultExchangeThrowsException() throws Exception
    {
        final String queueName = getName();
        final String customBinding = "custom-binding";
        File config = writeConfigFile(queueName, queueName, null, false, new String[]{customBinding});

        try
        {
            createVirtualHost(queueName, config);
            fail("virtualhost creation should have failed due to illegal configuration");
        }
        catch (RuntimeException e)
        {
            // PASS
            assertEquals("Failed to create virtual host", e.getMessage());

            assertNotNull(e.getCause());
            assertNotNull(e.getCause().getCause());

            assertEquals(ConfigurationException.class, e.getCause().getCause().getClass());

            Throwable configException = e.getCause().getCause();
            assertEquals("Illegal attempt to bind queue '" + queueName + "' to the default exchange with a key other than the queue name: " + customBinding, configException.getMessage());
        }
    }

    public void testVirtualHostBecomesActive() throws Exception
    {
        File config = writeConfigFile(getName(), getName(), getName() +".direct", false, new String[0]);
        VirtualHost vhost = createVirtualHost(getName(), config);
        assertNotNull(vhost);
        assertEquals(State.ACTIVE, vhost.getState());
    }

    public void testVirtualHostBecomesStoppedOnClose() throws Exception
    {
        File config = writeConfigFile(getName(), getName(), getName() +".direct", false, new String[0]);
        VirtualHost vhost = createVirtualHost(getName(), config);
        assertNotNull(vhost);
        assertEquals(State.ACTIVE, vhost.getState());
        vhost.close();
        assertEquals(State.STOPPED, vhost.getState());
        assertEquals(0, vhost.getHouseKeepingActiveCount());
    }

    /**
     * Tests that specifying an unknown exchange to bind the queue to results in failure to create the vhost
     */
    public void testSpecifyingUnknownExchangeThrowsException() throws Exception
    {
        final String queueName = getName();
        final String exchangeName = "made-up-exchange";
        File config = writeConfigFile(queueName, queueName, exchangeName, true, new String[0]);

        try
        {
            createVirtualHost(queueName, config);
            fail("virtualhost creation should have failed due to illegal configuration");
        }
        catch (RuntimeException e)
        {
            // PASS
            assertEquals("Failed to create virtual host", e.getMessage());

            assertNotNull(e.getCause());
            assertNotNull(e.getCause().getCause());

            assertEquals(ConfigurationException.class, e.getCause().getCause().getClass());

            Throwable configException = e.getCause().getCause();
            assertEquals("Attempt to bind queue '" + queueName + "' to unknown exchange:" + exchangeName, configException.getMessage());
        }
    }

    private void customBindingTestImpl(final String[] routingKeys) throws Exception
    {
        String exchangeName = getName() +".direct";
        String vhostName = getName();
        String queueName = getName();

        File config = writeConfigFile(vhostName, queueName, exchangeName, false, routingKeys);
        VirtualHost vhost = createVirtualHost(vhostName, config);
        assertNotNull("virtualhost should exist", vhost);

        AMQQueue queue = vhost.getQueueRegistry().getQueue(queueName);
        assertNotNull("queue should exist", queue);

        Exchange defaultExch = vhost.getExchangeRegistry().getDefaultExchange();
        assertTrue("queue should have been bound to default exchange with its name", defaultExch.isBound(queueName, queue));

        Exchange exch = vhost.getExchangeRegistry().getExchange(exchangeName);
        assertTrue("queue should have been bound to " + exchangeName + " with its name", exch.isBound(queueName, queue));

        for(String key: routingKeys)
        {
            assertTrue("queue should have been bound to " + exchangeName + " with key " + key, exch.isBound(key, queue));
        }
    }


    private VirtualHost createVirtualHost(String vhostName, File config) throws Exception
    {
        _virtualHostRegistry = BrokerTestHelper.createVirtualHostRegistry();
        recoverAndStartVirtualHost(vhostName, config);
        return _virtualHostRegistry.getVirtualHost(vhostName);
    }

    private void recoverAndStartVirtualHost(String vhostName, File config)
    {
        // broker mock object with security manager
        Broker broker = mock(Broker.class);
        when(broker.getSecurityManager()).thenReturn(new SecurityManager(null));

        // configuration entry
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(org.apache.qpid.server.model.VirtualHost.NAME, vhostName);
        attributes.put(org.apache.qpid.server.model.VirtualHost.CONFIGURATION, config.getAbsoluteFile());
        ConfigurationEntry entry = mock(ConfigurationEntry.class);
        when(entry.getId()).thenReturn(UUID.randomUUID());
        when(entry.getAttributes()).thenReturn(attributes);

        // recovering
        VirtualHostRecoverer recovever = new VirtualHostRecoverer(_virtualHostRegistry, _virtualHostRegistry.getApplicationRegistry());
        org.apache.qpid.server.model.VirtualHost model= recovever.create(null, entry, broker);

        // starting
        model.setDesiredState(org.apache.qpid.server.model.State.INITIALISING, org.apache.qpid.server.model.State.ACTIVE);
    }

    /**
     * Create a configuration file for testing virtualhost creation
     * 
     * @param vhostName name of the virtualhost
     * @param queueName name of the queue
     * @param exchangeName name of a direct exchange to declare (unless dontDeclare = true) and bind the queue to (null = none)
     * @param dontDeclare if true then dont declare the exchange, even if its name is non-null
     * @param routingKeys routingKeys to bind the queue with (empty array = none)
     * @return
     */
    private File writeConfigFile(String vhostName, String queueName, String exchangeName, boolean dontDeclare, String[] routingKeys)
    {
        File tmpFile = null;
        try
        {
            tmpFile = File.createTempFile(getName(), ".tmp");
            tmpFile.deleteOnExit();

            FileWriter fstream = new FileWriter(tmpFile);
            BufferedWriter writer = new BufferedWriter(fstream);

            //extra outer tag to please Commons Configuration

            writer.write("<virtualhosts>");
            writer.write("  <default>" + vhostName + "</default>");
            writer.write("  <virtualhost>");
            writer.write("      <name>" + vhostName + "</name>");
            writer.write("      <" + vhostName + ">");
            writer.write("              <store>");
            writer.write("                <class>" + MemoryMessageStore.class.getName() + "</class>");
            writer.write("              </store>");
            if(exchangeName != null && !dontDeclare)
            {
                writer.write("          <exchanges>");
                writer.write("              <exchange>");
                writer.write("                  <type>direct</type>");
                writer.write("                  <name>" + exchangeName + "</name>");
                writer.write("              </exchange>");
                writer.write("          </exchanges>");
            }
            writer.write("          <queues>");
            writer.write("              <queue>");
            writer.write("                  <name>" + queueName + "</name>");
            writer.write("                  <" + queueName + ">");
            if(exchangeName != null)
            {
                writer.write("                      <exchange>" + exchangeName + "</exchange>");
            }
            for(String routingKey: routingKeys)
            {
                writer.write("                      <routingKey>" + routingKey + "</routingKey>");
            }
            writer.write("                  </" + queueName + ">");
            writer.write("              </queue>");
            writer.write("          </queues>");
            writer.write("      </" + vhostName + ">");
            writer.write("  </virtualhost>");
            writer.write("</virtualhosts>");

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            fail("Unable to create virtualhost configuration");
        }

        return tmpFile;
    }
}
