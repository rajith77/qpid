/*
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
 */
package org.apache.qpid.amqp_0_10.jms.impl;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.NoSuchElementException;

import javax.jms.JMSException;
import javax.jms.QueueBrowser;

import org.apache.qpid.transport.util.Logger;

public class QueueBrowserImpl implements QueueBrowser
{
    private static final Logger _logger = Logger.get(QueueBrowserImpl.class);

    private QueueImpl _queue;
    private String _selector;
    private final SessionImpl _session;
    private HashSet<MessageEnumeration> _enumerations = new HashSet<MessageEnumeration>();
    private boolean _closed;

    QueueBrowserImpl(final QueueImpl queue, final String selector, SessionImpl session) throws JMSException
    {
        _queue = queue;
        _selector = selector;
        _session = session;

        // To verify the destination and the subscription information.
        new MessageEnumeration().close();
    }

    public QueueImpl getQueue()
    {
        return _queue;
    }

    public String getMessageSelector()
    {
        return _selector;
    }

    @SuppressWarnings("rawtypes")
    public Enumeration getEnumeration() throws JMSException
    {
        if (_closed)
        {
            throw new IllegalStateException("Browser has been closed");
        }
        return new MessageEnumeration();
    }

    public void close() throws JMSException
    {
        _closed = true;
        for (MessageEnumeration me : new ArrayList<MessageEnumeration>(_enumerations))
        {
            me.close();
        }
    }

    private final class MessageEnumeration implements Enumeration<MessageImpl>
    {
        private MessageConsumerImpl _receiver;
        private MessageImpl _nextElement;
        private boolean _needNext = true;

        MessageEnumeration() throws JMSException
        {
            try
            {
                _receiver = (MessageConsumerImpl) _session.createConsumer(_queue, _selector);
            }
            catch (JMSException e)
            {
                JMSException ex = new JMSException("Unable to create Queue Browser due to error");
                ex.setLinkedException(e);
                ex.initCause(e);
                throw ex;
            }
            _enumerations.add(this);

        }

        public void close()
        {
            _enumerations.remove(this);
            try
            {
                _receiver.close();
            }
            catch (JMSException e)
            {
                _logger.warn(e, "Unable to close receiver due to error");
            }
            _receiver = null;
        }

        @Override
        public boolean hasMoreElements()
        {
            if (_receiver == null)
            {
                return false;
            }
            if (_needNext)
            {
                _needNext = false;
                try
                {
                    // The receiveNoWait does a drain if prefetch is disabled.
                    _nextElement = _receiver.receiveNoWait();
                }
                catch (JMSException e)
                {
                    // Just log the message, the next block will close it.
                    _logger.warn(e, "Error trying to get next message for queue browser");
                }
                if (_nextElement == null)
                {
                    close();
                }
            }
            return _nextElement != null;
        }

        @Override
        public MessageImpl nextElement()
        {
            if (hasMoreElements())
            {
                MessageImpl message = _nextElement;
                _nextElement = null;
                _needNext = true;
                return message;
            }
            else
            {
                throw new NoSuchElementException();
            }
        }
    }
}