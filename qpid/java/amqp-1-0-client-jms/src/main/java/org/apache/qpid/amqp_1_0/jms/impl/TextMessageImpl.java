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

package org.apache.qpid.amqp_1_0.jms.impl;

import org.apache.qpid.amqp_1_0.jms.TextMessage;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.*;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

import javax.jms.JMSException;
import javax.jms.MessageNotWriteableException;
import java.util.*;

public class TextMessageImpl extends MessageImpl implements TextMessage
{
    private String _text;

    protected TextMessageImpl(Header header,
                              MessageAnnotations messageAnnotations,
                              Properties properties,
                              ApplicationProperties appProperties,
                              String text,
                              Footer footer,
                              SessionImpl session)
    {
        super(header, messageAnnotations, properties, appProperties, footer, session);
        _text = text;
    }

    protected TextMessageImpl(final SessionImpl session)
    {
        super(new Header(), new MessageAnnotations(new HashMap()),
              new Properties(), new ApplicationProperties(new HashMap()), new Footer(Collections.EMPTY_MAP),
              session);
    }

    public void setText(final String text) throws MessageNotWriteableException
    {
        if(isReadOnly())
        {
            throw new MessageNotWriteableException("Cannot set object, message is in read only mode");
        }

        _text = text;
    }

    public String getText() throws JMSException
    {
        return _text;
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _text = null;
    }

    @Override Collection<Section> getSections()
    {
        List<Section> sections = new ArrayList<Section>();
        sections.add(getHeader());
        if(getMessageAnnotations() != null && getMessageAnnotations().getValue() != null && !getMessageAnnotations().getValue().isEmpty())
        {
            sections.add(getMessageAnnotations());
        }
        sections.add(getProperties());
        sections.add(getApplicationProperties());
        AmqpValue section = new AmqpValue(_text);
        sections.add(section);
        sections.add(getFooter());
        return sections;
    }


}
