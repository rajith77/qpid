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
package org.apache.qpid.transport;

import static org.apache.qpid.transport.Connection.State.CLOSED;
import static org.apache.qpid.transport.Connection.State.OPEN;

import java.util.ArrayList;
import java.util.List;

import javax.security.sasl.SaslException;

import org.apache.qpid.transport.util.Waiter;

public class ClientConnection extends Connection
{
    private ConnectionException _exception; 
    
    public ClientConnection()
    {
        super();
    }

    @Override
    public Session createSession(Binary name, long expiry, boolean isNoReplay)
    {
        synchronized (lock)
        {
            Waiter w = new Waiter(lock, timeout);
            while (w.hasTime() && state != OPEN && _exception == null)
            {
                w.await();
            }

            if (state != OPEN)
            {
                if (connectionLost.get())
                {
                    throw _exception;
                }
                else
                {
                    throw new ConnectionException("Timed out waiting for connection to be ready. Current state is :" + state);
                }
            }

            Session ssn = _sessionFactory.newSession(this, name, expiry, isNoReplay);
            registerSession(ssn);
            map(ssn);
            ssn.attach();
            return ssn;
        }
    }

    @Override
    public void send(ProtocolEvent event)
    {
        _lastSendTime = System.currentTimeMillis();        
        if (log.isDebugEnabled())
        {
            log.debug("SEND: [%s] %s", this, event);
        }
        Sender<ProtocolEvent> s = sender;
        if (s == null)
        {
            if (connectionLost.get())
            {
                throw _exception;
            }
            else
            {
                throw new ConnectionException("connection-closed", _exception);
            }
        }
        try
        {
            s.send(event);
        }
        catch (TransportFailureException e)
        {
            throw e;
        }
        catch (SenderException e)
        {
            throw new ConnectionException("Sender closed", e);
        }
    }

    @Override
    public void exception(ConnectionException e)
    {        
        synchronized (lock)
        {
            _exception = e;
            switch (state)
            {
            case OPENING:
            case CLOSING:
                error = e;
                lock.notifyAll();
                return;
            }
        }

        for (ConnectionListener listener : listeners)
        {
            listener.exception(this, e);
        }
    }

    @Override
    public void exception(Throwable t)
    {
        if (t instanceof SaslException)
        {
            exception(new ConnectionException("authentication-failed", t));
        }
        else
        {
            connectionLost.set(true);
            exception(new TransportFailureException("connection-failed", t));
        }
    }
    
    @Override
    public void closed()
    {
        log.debug("connection closed: %s", this);

        synchronized (lock)
        {
            List<Session> values = new ArrayList<Session>(channels.values());
            for (Session ssn : values)
            {
                ssn.closed();
            }

            try
            {
                sender.close();
            }
            catch(Exception e)
            {
                // ignore.
            }
            setState(CLOSED);
            sender = null;
        }

        for (ConnectionListener listener: listeners)
        {
            listener.closed(this);
        }
    }
}