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
package org.apache.qpid.server.output;

import org.apache.qpid.AMQPInvalidClassException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.message.MessageTransferMessage;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageDeliveryMode;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.ReplyTo;

import java.util.HashMap;
import java.util.Map;

public class HeaderPropertiesConverter
{
    private HeaderPropertiesConverter()
    {
    }

    public static BasicContentHeaderProperties convert(MessageTransferMessage messageTransferMessage, VirtualHost vhost)
    {
        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        Header header = messageTransferMessage.getHeader();
        DeliveryProperties deliveryProps = header.getDeliveryProperties();
        MessageProperties messageProps = header.getMessageProperties();

        if(deliveryProps != null)
        {
            if(deliveryProps.hasDeliveryMode())
            {
                props.setDeliveryMode((byte)(deliveryProps.getDeliveryMode() == MessageDeliveryMode.PERSISTENT ? BasicContentHeaderProperties.PERSISTENT : BasicContentHeaderProperties.NON_PERSISTENT));
            }
            if(deliveryProps.hasExpiration())
            {
                props.setExpiration(deliveryProps.getExpiration());
            }
            if(deliveryProps.hasPriority())
            {
                props.setPriority((byte)deliveryProps.getPriority().getValue());
            }
            if(deliveryProps.hasTimestamp())
            {
                props.setTimestamp(deliveryProps.getTimestamp());
            }
        }
        if(messageProps != null)
        {
            if(messageProps.hasAppId())
            {
                props.setAppId(new AMQShortString(messageProps.getAppId()));
            }
            if(messageProps.hasContentType())
            {
                props.setContentType(messageProps.getContentType());
            }
            if(messageProps.hasCorrelationId())
            {
                props.setCorrelationId(new AMQShortString(messageProps.getCorrelationId()));
            }
            if(messageProps.hasContentEncoding())
            {
                props.setEncoding(messageProps.getContentEncoding());
            }
            if(messageProps.hasMessageId())
            {
                props.setMessageId("ID:" + messageProps.getMessageId().toString());
            }
            if(messageProps.hasReplyTo())
            {
                ReplyTo replyTo = messageProps.getReplyTo();
                String exchangeName = replyTo.getExchange();
                String routingKey = replyTo.getRoutingKey();
                if(exchangeName == null)
                {
                    exchangeName = "";
                }

                Exchange exchange = vhost.getExchangeRegistry().getExchange(exchangeName);
                String exchangeClass = exchange == null ? ExchangeDefaults.DIRECT_EXCHANGE_CLASS.asString() : exchange.getType().getName().asString();
                props.setReplyTo(exchangeClass + "://"+exchangeName+"//?routingkey='"+(routingKey==null ? "" : routingKey+"'"));

            }
            if(messageProps.hasUserId())
            {
                props.setUserId(new AMQShortString(messageProps.getUserId()));
            }

            if(messageProps.hasApplicationHeaders())
            {
                Map<String, Object> appHeaders = new HashMap<String, Object>(messageProps.getApplicationHeaders());
                if(messageProps.getApplicationHeaders().containsKey("x-jms-type"))
                {
                    props.setType(String.valueOf(appHeaders.remove("x-jms-type")));
                }

                FieldTable ft = new FieldTable();
                for(Map.Entry<String, Object> entry : appHeaders.entrySet())
                {
                    try
                    {
                        ft.put(new AMQShortString(entry.getKey()), entry.getValue());
                    }
                    catch(AMQPInvalidClassException e)
                    {
                        // TODO
                        // log here, but ignore - just can;t convert
                    }
                }
                props.setHeaders(ft);

            }
        }







        return props;
    }
}
