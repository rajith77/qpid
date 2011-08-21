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

package org.apache.qpid.amqp_1_0.messaging;

import org.apache.qpid.amqp_1_0.codec.ValueHandler;

import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class SectionDecoderImpl implements SectionDecoder
{

    private ValueHandler _valueHandler;


    public SectionDecoderImpl(final AMQPDescribedTypeRegistry describedTypeRegistry)
    {
        _valueHandler = new ValueHandler(describedTypeRegistry);
    }

    public List<Section> parseAll(ByteBuffer buf) throws AmqpErrorException
    {

        List<Section> obj = new ArrayList<Section>();
        while(buf.hasRemaining())
        {
            obj.add((Section) _valueHandler.parse(buf));

        }

        return obj;
    }

    public Section readSection(ByteBuffer buf) throws AmqpErrorException
    {
        return (Section) _valueHandler.parse(buf);
    }


}