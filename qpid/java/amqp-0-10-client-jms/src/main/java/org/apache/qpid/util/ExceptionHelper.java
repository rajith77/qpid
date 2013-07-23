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
package org.apache.qpid.util;

import java.io.EOFException;
import java.nio.charset.CharacterCodingException;

import javax.jms.JMSException;

import org.apache.qpid.AMQException;
import org.apache.qpid.transport.ConnectionCloseCode;
import org.apache.qpid.transport.ConnectionException;
import org.apache.qpid.transport.ExecutionErrorCode;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.typedmessage.TypedBytesFormatException;

public class ExceptionHelper
{
    public static JMSException toJMSException(String msg, Exception e)
    {
        if (e instanceof ConnectionException)
        {
            return toJMSException(msg, (ConnectionException) e);
        }
        else if (e instanceof SessionException)
        {
            return toJMSException(msg, (SessionException) e);
        }
        else if (e instanceof AMQException)
        {
            return toJMSException(msg, (AMQException) e);
        }
        else
        {
            JMSException ex = new JMSException(msg);
            ex.initCause(e);
            ex.setLinkedException(e);
            return ex;
        }
    }

    public static JMSException toJMSException(String msg, AMQException e)
    {
        JMSException ex = new JMSException(msg, e.getErrorCode().getName().asString());
        ex.initCause(e);
        ex.setLinkedException(e);
        return ex;
    }

    public static JMSException toJMSException(String msg, ConnectionException ce)
    {
        String code = ConnectionCloseCode.NORMAL.name();
        if (ce.getClose() != null && ce.getClose().getReplyCode() != null)
        {
            code = ce.getClose().getReplyCode().name();
        }
        JMSException ex = new JMSException(msg, code);
        ex.initCause(ce);
        ex.setLinkedException(ce);
        return ex;
    }

    public static JMSException toJMSException(String msg, SessionException se)
    {
        String code = "UNSPECIFIED";
        ExecutionErrorCode executionError = ExecutionErrorCode.INTERNAL_ERROR;
        if (se.getException() != null && se.getException().getErrorCode() != null)
        {
            code = se.getException().getErrorCode().name();
            executionError = se.getException().getErrorCode();
        }
        if (executionError == ExecutionErrorCode.UNAUTHORIZED_ACCESS || executionError == ExecutionErrorCode.NOT_ALLOWED)
        {
            JMSException ex = new javax.jms.JMSSecurityException(msg, code);
            ex.initCause(se);
            ex.setLinkedException(se);
            return ex;
        }
        else if (executionError == ExecutionErrorCode.ILLEGAL_STATE)
        {
            JMSException ex = new javax.jms.IllegalStateException(msg, code);
            ex.initCause(se);
            ex.setLinkedException(se);
            return ex;
        }
        else
        {
            JMSException ex = new JMSException(msg, code);
            ex.initCause(se);
            ex.setLinkedException(se);
            return ex;
        }
    }

    public static JMSException handleMessageException(String msg, Exception e)
    {
        if (e instanceof EOFException)
        {
            javax.jms.MessageEOFException ex = new javax.jms.MessageEOFException(msg);
            ex.initCause(e);
            ex.setLinkedException(e);
            return ex;
        }
        else if (e instanceof TypedBytesFormatException || e instanceof CharacterCodingException)
        {
            javax.jms.MessageFormatException ex = new javax.jms.MessageFormatException(msg);
            ex.initCause(e);
            ex.setLinkedException(e);
            return ex;
        }
        else
        {
            JMSException ex = new JMSException(msg);
            ex.initCause(e);
            ex.setLinkedException(e);
            return ex;
        }
    }
}
