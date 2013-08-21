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

import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.transport.network.*;
import org.apache.qpid.transport.network.security.SecurityLayer;
import org.apache.qpid.transport.network.security.SecurityLayerFactory;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.transport.util.Waiter;
import org.apache.qpid.util.Strings;

import static org.apache.qpid.transport.Connection.State.CLOSED;
import static org.apache.qpid.transport.Connection.State.CLOSING;
import static org.apache.qpid.transport.Connection.State.NEW;
import static org.apache.qpid.transport.Connection.State.OPEN;
import static org.apache.qpid.transport.Connection.State.OPENING;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslServer;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Connection
 *
 * @author Rafael H. Schloming
 *
 * @todo the channels map should probably be replaced with something
 * more efficient, e.g. an array or a map implementation that can use
 * short instead of Short
 */

public class Connection extends ConnectionInvoker
    implements Receiver<ProtocolEvent>, Sender<ProtocolEvent>
{

    protected static final Logger log = Logger.get(Connection.class);

    //Usable channels are numbered 0 to <ChannelMax> - 1
    public static final int MAX_CHANNEL_MAX = 0xFFFF;
    public static final int MIN_USABLE_CHANNEL_NUM = 0;
    protected long _lastSendTime;
    private long _lastReadTime;


    public enum State { NEW, CLOSED, OPENING, OPEN, CLOSING, CLOSE_RCVD, RESUMING }

    static class DefaultConnectionListener implements ConnectionListener
    {
        public void opened(Connection conn) {}
        public void exception(Connection conn, ConnectionException exception)
        {
            log.error(exception, "connection exception");
        }
        public void closed(Connection conn) {}
    }

    public static interface SessionFactory
    {
        Session newSession(Connection conn, Binary name, long expiry, boolean isNoReplay);
    }

    private static final class DefaultSessionFactory implements SessionFactory
    {

        public Session newSession(final Connection conn, final Binary name, final long expiry, final boolean isNoReplay)
        {
            return new Session(conn, name, expiry, isNoReplay);
        }
    }

    private static final SessionFactory DEFAULT_SESSION_FACTORY = new DefaultSessionFactory();

    private SessionFactory _sessionFactory = DEFAULT_SESSION_FACTORY;

    private ConnectionDelegate delegate;
    protected Sender<ProtocolEvent> sender;

    final private Map<Binary,Session> sessions = new HashMap<Binary,Session>();
    final protected Map<Integer,Session> channels = new HashMap<Integer,Session>();

    protected State state = NEW;
    final protected Object lock = new Object();
    private long timeout = 60000;
    protected List<ConnectionListener> listeners = new ArrayList<ConnectionListener>();
    protected ConnectionException error = null;

    private int channelMax = 1;
    private String locale;
    private SaslServer saslServer;
    private SaslClient saslClient;
    private int idleTimeout = 0;
    private Map<String,Object> _serverProperties;
    private String userID;
    private ConnectionSettings conSettings;
    private SecurityLayer securityLayer;

    protected final AtomicBoolean connectionLost = new AtomicBoolean(false);

    private SocketAddress _remoteAddress;
    private SocketAddress _localAddress;

    public Connection() {}

    public void setConnectionDelegate(ConnectionDelegate delegate)
    {
        this.delegate = delegate;
    }

    public void addConnectionListener(ConnectionListener listener)
    {
        listeners.add(listener);
    }

    public void removeConnectionListener(ConnectionListener listener)
    {
        listeners.add(listener);
    }

    public Sender<ProtocolEvent> getSender()
    {
        return sender;
    }

    public void setSender(Sender<ProtocolEvent> sender)
    {
        this.sender = sender;
        sender.setIdleTimeout(idleTimeout);
    }

    protected void setState(State state)
    {
        synchronized (lock)
        {
            this.state = state;
            lock.notifyAll();
        }
    }

    void setLocale(String locale)
    {
        this.locale = locale;
    }

    String getLocale()
    {
        return locale;
    }

    void setSaslServer(SaslServer saslServer)
    {
        this.saslServer = saslServer;
    }

    SaslServer getSaslServer()
    {
        return saslServer;
    }

    void setSaslClient(SaslClient saslClient)
    {
        this.saslClient = saslClient;
    }

    public SaslClient getSaslClient()
    {
        return saslClient;
    }

    public void connect(String host, int port, String vhost, String username, String password, boolean ssl, String saslMechs)
    {
        connect(host, port, vhost, username, password, ssl, saslMechs, null);
    }

    public void connect(String host, int port, String vhost, String username, String password, boolean ssl, String saslMechs, Map<String,Object> clientProps)
    {
        ConnectionSettings settings = new DefaultConnectionSettingsImpl(host, port, vhost, username, password, ssl, saslMechs, clientProps);
        connect(settings);
    }

    public void connect(ConnectionSettings settings)
    {

        synchronized (lock)
        {
            conSettings = settings;
            state = OPENING;
            userID = settings.getUsername();
            connectionLost.set(false);

            securityLayer = SecurityLayerFactory.newInstance(getConnectionSettings());

            OutgoingNetworkTransport transport = Transport.getOutgoingTransportInstance(ProtocolVersion.v0_10);
            Receiver<ByteBuffer> secureReceiver = securityLayer.receiver(new InputHandler(new Assembler(this)));
            if(secureReceiver instanceof ConnectionListener)
            {
                addConnectionListener((ConnectionListener)secureReceiver);
            }

            NetworkConnection network = transport.connect(settings, secureReceiver, new ConnectionActivity());

            setRemoteAddress(network.getRemoteAddress());
            setLocalAddress(network.getLocalAddress());

            final Sender<ByteBuffer> secureSender = securityLayer.sender(network.getSender());
            if(secureSender instanceof ConnectionListener)
            {
                addConnectionListener((ConnectionListener)secureSender);
            }
            sender = new Disassembler(secureSender, settings.getMaxFrameSize());

            send(new ProtocolHeader(1, 0, 10));

            Waiter w = new Waiter(lock, timeout);
            while (w.hasTime() && state == OPENING && error == null)
            {
                w.await();
            }

            if (error != null)
            {
                ConnectionException t = error;
                error = null;
                try
                {
                    close();
                }
                catch (ConnectionException ce)
                {
                    if (!(t instanceof ProtocolVersionException))
                    {
                        throw ce;
                    }
                }
                t.rethrow();
            }

            switch (state)
            {
            case OPENING:
                close();
                throw new ConnectionException("connect() timed out");
            case OPEN:
            case RESUMING:
                connectionLost.set(false);
                break;
            case CLOSED:
                throw new ConnectionException("connect() aborted");
            default:
                throw new IllegalStateException(String.valueOf(state));
            }
        }

        for (ConnectionListener listener: listeners)
        {
            listener.opened(this);
        }
    }

    public Session createSession()
    {
        return createSession(0);
    }

    public Session createSession(long expiry)
    {
        return createSession(expiry, false);
    }

    public Session createSession(long expiry, boolean isNoReplay)
    {
        return createSession(UUID.randomUUID().toString(), expiry, isNoReplay);
    }

    public Session createSession(String name)
    {
        return createSession(name, 0);
    }

    public Session createSession(String name, long expiry)
    {
        return createSession(Strings.toUTF8(name), expiry);
    }

    public Session createSession(String name, long expiry,boolean isNoReplay)
    {
        return createSession(new Binary(Strings.toUTF8(name)), expiry, isNoReplay);
    }

    public Session createSession(byte[] name, long expiry)
    {
        return createSession(new Binary(name), expiry);
    }

    public Session createSession(Binary name, long expiry)
    {
        return createSession(name, expiry, false);
    }

    public Session createSession(Binary name, long expiry, boolean isNoReplay)
    {
        synchronized (lock)
        {
            Waiter w = new Waiter(lock, timeout);
            while (w.hasTime() && state != OPEN && error == null)
            {
                w.await();
            }

            if (state != OPEN)
            {
                throw new ConnectionException("Timed out waiting for connection to be ready. Current state is :" + state);
            }

            Session ssn = _sessionFactory.newSession(this, name, expiry, isNoReplay);
            registerSession(ssn);
            map(ssn);
            ssn.attach();
            return ssn;
        }
    }

    public void registerSession(Session ssn)
    {
        synchronized (lock)
        {
            sessions.put(ssn.getName(),ssn);
        }
    }

    public void removeSession(Session ssn)
    {
        synchronized (lock)
        {
            sessions.remove(ssn.getName());
        }
    }

    public void setSessionFactory(SessionFactory sessionFactory)
    {
        assert sessionFactory != null;

        _sessionFactory = sessionFactory;
    }

    public ConnectionDelegate getConnectionDelegate()
    {
        return delegate;
    }

    public void received(ProtocolEvent event)
    {
        _lastReadTime = System.currentTimeMillis();
        if(log.isDebugEnabled())
        {
            log.debug("RECV: [%s] %s", this, event);
        }
        event.delegate(this, delegate);
    }

    public void send(ProtocolEvent event)
    {
        _lastSendTime = System.currentTimeMillis();
        if(log.isDebugEnabled())
        {
            log.debug("SEND: [%s] %s", this, event);
        }
        Sender<ProtocolEvent> s = sender;
        if (s == null)
        {
            throw new ConnectionException("connection closed");
        }
        s.send(event);
    }

    public void flush()
    {
        if(log.isDebugEnabled())
        {
            log.debug("FLUSH: [%s]", this);
        }
        final Sender<ProtocolEvent> theSender = sender;
        if(theSender != null)
        {
            theSender.flush();
        }
    }

    protected void invoke(Method method)
    {
        method.setChannel(0);
        send(method);
        if (!method.isBatch())
        {
            flush();
        }
    }

    public void dispatch(Method method)
    {
        int channel = method.getChannel();
        Session ssn = getSession(channel);
        if(ssn != null)
        {
            ssn.received(method);
        }
        else
        {
            /*
             * A peer receiving any other control on a detached transport MUST discard it and
             * send a session.detached with the "not-attached" reason code.
             */
            if(log.isDebugEnabled())
            {
                log.debug("Control received on unattached channel : %d", channel);
            }
            invokeSessionDetached(channel, SessionDetachCode.NOT_ATTACHED);
        }
    }

    public int getChannelMax()
    {
        return channelMax;
    }

    void setChannelMax(int max)
    {
        channelMax = max;
    }

    private int map(Session ssn)
    {
        synchronized (lock)
        {
            //For a negotiated channelMax N, there are channels 0 to N-1 available.
            for (int i = 0; i < getChannelMax(); i++)
            {
                if (!channels.containsKey(i))
                {
                    map(ssn, i);
                    return i;
                }
            }

            throw new RuntimeException("no more channels available");
        }
    }

    void map(Session ssn, int channel)
    {
        synchronized (lock)
        {
            channels.put(channel, ssn);
            ssn.setChannel(channel);
        }
    }

    void unmap(Session ssn)
    {
        synchronized (lock)
        {
            channels.remove(ssn.getChannel());
        }
    }

    public Session getSession(int channel)
    {
        synchronized (lock)
        {
            return channels.get(channel);
        }
    }

    public void resume()
    {
        synchronized (lock)
        {
            for (Session ssn : sessions.values())
            {
                map(ssn);
                ssn.resume();
            }

            setState(OPEN);
        }
    }

    public void exception(ConnectionException e)
    {
        connectionLost.set(true);
        synchronized (lock)
        {
            switch (state)
            {
            case OPENING:
            case CLOSING:
                error = e;
                lock.notifyAll();
                return;
            }
        }

        for (ConnectionListener listener: listeners)
        {
            listener.exception(this, e);
        }

    }

    public void exception(Throwable t)
    {
        exception(new ConnectionException(t));
    }

    public void closeCode(ConnectionClose close)
    {
        synchronized (lock)
        {
            ConnectionCloseCode code = close.getReplyCode();
            if (code != ConnectionCloseCode.NORMAL)
            {
                exception(new ConnectionException(close));
            }
        }
    }

    public void closed()
    {
        if (state == OPEN)
        {
            exception(new ConnectionException("connection aborted"));
        }

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
            sender = null;
            setState(CLOSED);
        }

        for (ConnectionListener listener: listeners)
        {
            listener.closed(this);
        }
    }

    public void close()
    {
        close(ConnectionCloseCode.NORMAL, null);
    }

    public void mgmtClose()
    {
        close(ConnectionCloseCode.CONNECTION_FORCED, "The connection was closed using the broker's management interface.");
    }

    public void close(ConnectionCloseCode replyCode, String replyText, Option ... _options)
    {
        synchronized (lock)
        {
            switch (state)
            {
            case OPEN:
                state = CLOSING;
                connectionClose(replyCode, replyText, _options);
                Waiter w = new Waiter(lock, timeout);
                while (w.hasTime() && state == CLOSING && error == null)
                {
                    w.await();
                }

                if (error != null)
                {
                    close(replyCode, replyText, _options);
                    throw new ConnectionException(error);
                }

                switch (state)
                {
                case CLOSING:
                    close(replyCode, replyText, _options);
                    throw new ConnectionException("close() timed out");
                case CLOSED:
                    break;
                default:
                    throw new IllegalStateException(String.valueOf(state));
                }
                break;
            case CLOSED:
                break;
            default:
                if (sender != null)
                {
                    sender.close();
                    w = new Waiter(lock, timeout);
                    while (w.hasTime() && sender != null && error == null)
                    {
                        w.await();
                    }

                    if (error != null)
                    {
                        throw new ConnectionException(error);
                    }

                    if (sender != null)
                    {
                        throw new ConnectionException("close() timed out");
                    }
                }
                break;
            }
        }
    }

    public void setIdleTimeout(int i)
    {
        idleTimeout = i;
        if (sender != null)
        {
            sender.setIdleTimeout(i);
        }
    }

    public int getIdleTimeout()
    {
        return idleTimeout;
    }

    public String getUserID()
    {
        return userID;
    }

    public void setUserID(String id)
    {
        userID = id;
    }

    public void setServerProperties(final Map<String, Object> serverProperties)
    {
        _serverProperties = serverProperties == null ? Collections.<String, Object>emptyMap() : serverProperties;
    }

    public Map<String, Object> getServerProperties()
    {
        return _serverProperties;
    }

    public String toString()
    {
        return String.format("conn:%x", System.identityHashCode(this));
    }

    public ConnectionSettings getConnectionSettings()
    {
        return conSettings;
    }

    public SecurityLayer getSecurityLayer()
    {
        return securityLayer;
    }

    public boolean isConnectionResuming()
    {
        return connectionLost.get();
    }

    protected Collection<Session> getChannels()
    {
        return channels.values();
    }

    public boolean hasSessionWithName(final byte[] name)
    {
        return sessions.containsKey(new Binary(name));
    }

    public void notifyFailoverRequired()
    {
        List<Session> values = new ArrayList<Session>(channels.values());
        for (Session ssn : values)
        {
            ssn.notifyFailoverRequired();
        }
    }

    public SocketAddress getRemoteAddress()
    {
        return _remoteAddress;
    }

    public SocketAddress getLocalAddress()
    {
        return _localAddress;
    }

    protected void setRemoteAddress(SocketAddress remoteAddress)
    {
        _remoteAddress = remoteAddress;
    }

    protected void setLocalAddress(SocketAddress localAddress)
    {
        _localAddress = localAddress;
    }


    private void invokeSessionDetached(int channel, SessionDetachCode sessionDetachCode)
    {
        SessionDetached sessionDetached = new SessionDetached();
        sessionDetached.setChannel(channel);
        sessionDetached.setCode(sessionDetachCode);
        invoke(sessionDetached);
    }


    protected void doHeartBeat()
    {
        connectionHeartbeat();
    }

    private class ConnectionActivity implements TransportActivity
    {
        @Override
        public long getLastReadTime()
        {
            return _lastReadTime;
        }

        @Override
        public long getLastWriteTime()
        {
            return _lastSendTime;
        }

        @Override
        public void writerIdle()
        {
            connectionHeartbeat();
        }

        @Override
        public void readerIdle()
        {
            // TODO

        }
    }
}
