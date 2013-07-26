package org.apache.qpid.transport;

import static org.apache.qpid.transport.Session.State.CLOSED;
import static org.apache.qpid.transport.Session.State.OPEN;

import org.apache.qpid.transport.network.Frame;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.transport.util.Waiter;

/**
 * Failover and replay is removed. The JMS layer handles it. Sync is issued
 * instead of Flush, when command limit is reached.
 * 
 */
public class ClientSession extends Session
{
    private static final Logger _log = Logger.get(ClientSession.class);

    private ClientSessionListener _extendedListener;

    public ClientSession(Connection connection, SessionDelegate delegate, Binary name, long expiry, boolean noReplay)
    {
        super(connection, delegate, name, expiry, noReplay);
    }

    @Override
    public void setSessionListener(SessionListener listener)
    {
        super.setSessionListener(listener);
        if (listener instanceof ClientSessionListener)
        {
            _extendedListener = (ClientSessionListener) listener;
        }
        else
        {
            // For developers
            throw new IllegalArgumentException(
                    "This impl relies on the listener impelementing the extended interface ClientSessionListener");
        }
    }

    @Override
    void resume()
    {
        // For developers
        throw new UnsupportedOperationException("This impl does not support failover");
    }

    @Override
    void notifyCompletion(int id)
    {
        _extendedListener.commandCompleted(this, id);
    }

    @Override
    public void invoke(Method m, Runnable postIdSettingAction)
    {
        if (m.getEncodedTrack() == Frame.L4)
        {
            if (m.hasPayload())
            {
                acquireCredit();
            }

            synchronized (commandsLock)
            {
                switch (state)
                {
                case NEW:
                    Waiter w = new Waiter(commandsLock, timeout);
                    while (w.hasTime() && (state != OPEN && state != CLOSED))
                    {
                        w.await();
                    }
                case OPEN:
                    break;
                case CLOSING:
                case CLOSED:
                    ExecutionException exc = getException();
                    if (exc != null)
                    {
                        throw new SessionException(exc);
                    }
                    else
                    {
                        throw new SessionClosedException();
                    }
                default:
                    throw new SessionTimeoutException(String.format("timed out waiting for session to become open "
                            + "(state=%s)", state));
                }

                int next;
                next = commandsOut++;
                m.setId(next);
                if (postIdSettingAction != null)
                {
                    postIdSettingAction.run();
                }

                if (isFull(next))
                {
                    _log.info("Reached command limit, waiting for completion");
                    sync(timeout);
                }

                if (next == 0)
                {
                    sessionCommandPoint(0, 0);
                }

                if (autoSync)
                {
                    m.setSync(true);
                }
                needSync = !m.isSync();

                try
                {
                    send(m);
                }
                catch (SenderException e)
                {
                    e.rethrow();
                }

                if (autoSync)
                {
                    sync();
                }

                // flush every 64K commands to avoid ambiguity on
                // wraparound
                if (shouldIssueFlush(next))
                {
                    _log.info("Reached command limit, waiting for completion");
                    sync(timeout);
                }
            }
        }
        else
        {
            send(m);
        }
    }

    @Override
    public void closed()
    {
        synchronized (commandsLock)
        {
            state = CLOSED;
            wakeUpWaitingThreads();
            delegate.closed(this);
        }
        connection.removeSession(this);
        listener.closed(this);
    }
}