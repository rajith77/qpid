/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.amqp_0_10.jms;

import java.nio.ByteBuffer;

import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.apache.qpid.jms.ListMessage;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;

public interface MessageFactory
{
    /**
     * Supported Message Types. Use
     */
    public enum MessageType
    {
        BINARY, STRING, MAP, LIST
    }

    public Message createMessage(Session ssn);

    public TextMessage createTextMessage(Session ssn);

    public MapMessage createMapMessage(Session ssn);

    public ObjectMessage createObjectMessage(Session ssn);

    public StreamMessage createStreamMessage(Session ssn);

    public ListMessage createListMessage(Session ssn);

    public Message createMessage(Session ssn, DeliveryProperties deliveryProps, MessageProperties msgProps,
            ByteBuffer data);

    /**
     * You could use this method to map your custom content-type to one of the
     * supported MessageType's (@see MessageType), provided the content of the
     * message conforms to the expected type.
     * 
     * Ex. foo/bar -> STRING, will tell the client to treat any message that has
     * the content-type foo/bar to be treated as a STRING Message.
     * 
     * Currently supported content types are as follows.
     * <ul>
     * <li>default - BINARY</li>
     * <li>application/octet-stream - BINARY</li>
     * <li>text/plain - STRING</li>
     * <li>text/xml - STRING</li>
     * <li>amqp/map - MAP</li>
     * <li>amqp-0-10/map - MAP</li>
     * <li>amqp/list - LIST</li>
     * <li>amqp-0-10/list - LIST</li>
     * 
     * @param contentType
     *            The content type you want to register.
     * @param type
     *            The MessageType @see MessageType
     */
    public void registerContentType(String contentType, MessageType type);
}
