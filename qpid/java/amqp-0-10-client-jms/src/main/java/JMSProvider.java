import java.lang.reflect.Constructor;

import javax.jms.Connection;

public class JMSProvider
{
    private static Constructor<? extends Connection> _cons;

    static
    {
        try
        {
            String className = System.getProperty("qpid.connection", "org.apache.qpid.client.AMQConnection");
            Class<? extends Connection> clazz = Class.forName(className).asSubclass(Connection.class);
            _cons = clazz.getConstructor(String.class);
        }
        catch (Exception e)
        {
            throw new Error("Error loading connection constructor", e);
        }
    }

    public static Connection getConnection(String url) throws Exception
    {
        try
        {
            return _cons.newInstance(url);
        }
        catch (Exception e)
        {
            throw new Exception("Error creating connection", e);
        }
    }
}