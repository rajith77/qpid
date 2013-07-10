package org.apache.qpid.util;

import javax.jms.JMSException;

import org.apache.qpid.amqp_0_10.jms.MessageFactory;

public class MessageFactorySupport
{
    private final static String DEFAULT_MESSAGE_FACTORY_CLASS_NAME = "org.apache.qpid.amqp_0_10.jms.impl.message.MessageFactoryImpl";
    private final static Class<? extends MessageFactory> DEFAULT_MESSAGE_FACTORY_CLASS;
    private final static MessageFactory DEFAULT_MESSAGE_FACTORY_INSTANCE;

    static
    {
        try
        {
            DEFAULT_MESSAGE_FACTORY_CLASS = Class.forName(
                    System.getProperty("qpid.message_factory", DEFAULT_MESSAGE_FACTORY_CLASS_NAME)).asSubclass(
                    MessageFactory.class);
            DEFAULT_MESSAGE_FACTORY_INSTANCE = DEFAULT_MESSAGE_FACTORY_CLASS.newInstance();
        }
        catch (Exception e)
        {
            throw new Error("Unable to load message factory");
        }
    }

    public static MessageFactory getMessageFactory(String className) throws JMSException
    {
        try
        {
            if (className == null)
            {
                return DEFAULT_MESSAGE_FACTORY_INSTANCE;
            }
            else
            {
                Class<? extends MessageFactory> clazz = Class.forName(className).asSubclass(MessageFactory.class);
                return clazz.newInstance();
            }
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error getting message factory instance", e);
        }
    }
}