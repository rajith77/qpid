package org.apache.qpid.amqp_0_10.jms.impl.failover;

import javax.jms.JMSException;

import org.apache.qpid.amqp_0_10.jms.FailoverManager;
import org.apache.qpid.util.ExceptionHelper;

public class FailoverManagerSupport
{
    private final static String DEFAULT_FAILOVER_MANAGER_CLASS_NAME = "org.apache.qpid.amqp_0_10.jms.impl.failover.FailoverManagerImpl";

    private final static Class<? extends FailoverManager> DEFAULT_FAILOVER_MANAGER_CLASS;

    static
    {
        try
        {
            DEFAULT_FAILOVER_MANAGER_CLASS = Class.forName(
                    System.getProperty("qpid.failover_manager", DEFAULT_FAILOVER_MANAGER_CLASS_NAME)).asSubclass(
                    FailoverManager.class);
        }
        catch (Exception e)
        {
            Error er = new Error("Unable to load default FailoverManager class");
            er.initCause(e);
            throw er;
        }
    }

    public static FailoverManager getFailoverManager(String className) throws JMSException
    {
        try
        {
            if (className == null)
            {
                return DEFAULT_FAILOVER_MANAGER_CLASS.newInstance();
            }
            else
            {
                Class<? extends FailoverManager> clazz = Class.forName(className).asSubclass(FailoverManager.class);
                return clazz.newInstance();
            }
        }
        catch (Exception e)
        {
            throw ExceptionHelper.toJMSException("Error getting FailoverManager instance", e);
        }
    }
}