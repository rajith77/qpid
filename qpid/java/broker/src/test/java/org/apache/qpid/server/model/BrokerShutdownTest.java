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
package org.apache.qpid.server.model;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.startup.DefaultRecovererProvider;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.auth.manager.TestAuthenticationManagerFactory;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;

import java.security.Provider;
import java.security.Security;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * QPID-1390 : Test to validate that the AuthenticationManger can successfully unregister any new SASL providers when
 * the broker is stopped.
 */
public class BrokerShutdownTest extends QpidTestCase
{

    private Provider[] _defaultProviders;
    private Broker _broker;

    @Override
    public void setUp() throws Exception
    {
        // Get default providers
        _defaultProviders = Security.getProviders();

        super.setUp();

        // Startup the new broker and register the new providers
        _broker = startBroker();
    }

    private Broker startBroker()
    {
        // test store with only broker and authentication provider entries
        ConfigurationEntryStore store = new ConfigurationEntryStore()
        {
            private UUID _brokerId = UUID.randomUUID();
            private UUID _authenticationProviderId = UUID.randomUUID();

            @Override
            public ConfigurationEntry getRootEntry()
            {
                return new ConfigurationEntry(_brokerId, Broker.class.getSimpleName(), Collections.<String, Object> emptyMap(),
                        Collections.singleton(_authenticationProviderId), this);
            }

            @Override
            public ConfigurationEntry getEntry(UUID id)
            {
                if (_authenticationProviderId.equals(id))
                {
                    Map<String, Object> attributes = Collections.<String, Object> singletonMap(AuthenticationManagerFactory.ATTRIBUTE_TYPE,
                            TestAuthenticationManagerFactory.TEST_AUTH_MANAGER_MARKER);
                    return new ConfigurationEntry(_authenticationProviderId, AuthenticationProvider.class.getSimpleName(), attributes,
                            Collections.<UUID> emptySet(), this);
                }
                return null;
            }

            @Override
            public void save(ConfigurationEntry... entries)
            {
            }

            @Override
            public void remove(UUID... entryIds)
            {
            }

        };

        // mocking the registry, we still need it
        IApplicationRegistry registry = BrokerTestHelper.createMockApplicationRegistry();

        // recover the broker from the store
        RecovererProvider provider = new DefaultRecovererProvider(registry);
        ConfiguredObjectRecoverer<? extends ConfiguredObject> brokerRecoverer = provider.getRecoverer(Broker.class.getSimpleName());
        Broker broker = (Broker) brokerRecoverer.create(provider, store.getRootEntry());

        // start broker
        broker.setDesiredState(State.INITIALISING, State.ACTIVE);
        return broker;
    }

    private void stopBroker()
    {
        _broker.setDesiredState(State.ACTIVE, State.STOPPED);
    }

    /**
     * QPID-1399 : Ensure that the Authentication manager unregisters any SASL providers created during
     * broker start-up.
     *
     */
    public void testAuthenticationMangerCleansUp() throws Exception
    {

        // Get the providers after initialisation
        Provider[] providersAfterInitialisation = Security.getProviders();

        // Find the additions
        List<Provider> additions = new LinkedList<Provider>();
        for (Provider afterInit : providersAfterInitialisation)
        {
            boolean found = false;
            for (Provider defaultProvider : _defaultProviders)
            {
                if (defaultProvider == afterInit)
                {
                    found = true;
                    break;
                }
            }

            // Record added registies
            if (!found)
            {
                additions.add(afterInit);
            }
        }

        assertFalse("No new SASL mechanisms added by initialisation.", additions.isEmpty());

        // Close the registry which will perform the close the
        // AuthenticationManager
        stopBroker();

        // Validate that the SASL plugins have been removed.
        Provider[] providersAfterClose = Security.getProviders();

        assertTrue("No providers unregistered", providersAfterInitialisation.length > providersAfterClose.length);

        // Ensure that the additions are not still present after close().
        for (Provider afterClose : providersAfterClose)
        {
            assertFalse("Added provider not unregistered", additions.contains(afterClose));
        }
    }

}
