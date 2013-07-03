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

import javax.jms.JMSException;
import javax.jms.Session;

public enum AcknowledgeMode
{
    NO_ACK, AUTO_ACK, DUPS_OK, CLIENT_ACK, TRANSACTED;

    public static AcknowledgeMode getAckMode(int ackMode) throws JMSException
    {
        switch (ackMode)
        {
        case Session.AUTO_ACKNOWLEDGE:
            return AUTO_ACK;
        case Session.DUPS_OK_ACKNOWLEDGE:
            return DUPS_OK;
        case Session.CLIENT_ACKNOWLEDGE:
            return CLIENT_ACK;
        case Session.SESSION_TRANSACTED:
            return TRANSACTED;
        case 257:
            return NO_ACK;
        default:
            throw new JMSException("Invalid message acknowlege mode");
        }
    }

    public static int getJMSAckMode(AcknowledgeMode mode)
    {
        switch (mode)
        {
        case AUTO_ACK:
            return Session.AUTO_ACKNOWLEDGE;
        case DUPS_OK:
            return Session.DUPS_OK_ACKNOWLEDGE;
        case CLIENT_ACK:
            return Session.CLIENT_ACKNOWLEDGE;
        case TRANSACTED:
            return Session.SESSION_TRANSACTED;
        default:
            return 257; //NO_ACK
        }
    }
}