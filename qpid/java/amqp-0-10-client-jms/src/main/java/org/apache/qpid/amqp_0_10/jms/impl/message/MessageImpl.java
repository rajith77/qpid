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
package org.apache.qpid.amqp_0_10.jms.impl.message;

import java.util.Enumeration;

import javax.jms.Destination;
import javax.jms.JMSException;

import org.apache.qpid.amqp_0_10.jms.Message;

public class MessageImpl implements Message
{

    @Override
    public void acknowledge() throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void clearBody() throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void clearProperties() throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public boolean getBooleanProperty(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public byte getByteProperty(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getDoubleProperty(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getFloatProperty(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getIntProperty(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getJMSCorrelationID() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public byte[] getJMSCorrelationIDAsBytes() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getJMSDeliveryMode() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Destination getJMSDestination() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getJMSExpiration() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getJMSMessageID() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getJMSPriority() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean getJMSRedelivered() throws JMSException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Destination getJMSReplyTo() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getJMSTimestamp() throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getJMSType() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getLongProperty(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Object getObjectProperty(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Enumeration getPropertyNames() throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public short getShortProperty(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getStringProperty(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean propertyExists(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setBooleanProperty(String arg0, boolean arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setByteProperty(String arg0, byte arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setDoubleProperty(String arg0, double arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setFloatProperty(String arg0, float arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setIntProperty(String arg0, int arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setJMSCorrelationID(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setJMSCorrelationIDAsBytes(byte[] arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setJMSDeliveryMode(int arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setJMSDestination(Destination arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setJMSExpiration(long arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setJMSMessageID(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setJMSPriority(int arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setJMSRedelivered(boolean arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setJMSReplyTo(Destination arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setJMSTimestamp(long arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setJMSType(String arg0) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setLongProperty(String arg0, long arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setObjectProperty(String arg0, Object arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setShortProperty(String arg0, short arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setStringProperty(String arg0, String arg1) throws JMSException
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    public int getTransferId()
    {
        // TODO Auto-generated method stub
        return 0;
    }

}