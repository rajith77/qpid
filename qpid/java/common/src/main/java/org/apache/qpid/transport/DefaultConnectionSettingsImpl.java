package org.apache.qpid.transport;

import static org.apache.qpid.configuration.ClientProperties.AMQJ_TCP_NODELAY_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.LEGACY_RECEIVE_BUFFER_SIZE_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.LEGACY_SEND_BUFFER_SIZE_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_SSL_KEY_MANAGER_FACTORY_ALGORITHM_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_SSL_KEY_STORE_CERT_TYPE_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_SSL_TRUST_MANAGER_FACTORY_ALGORITHM_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_SSL_TRUST_STORE_CERT_TYPE_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_TCP_NODELAY_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.RECEIVE_BUFFER_SIZE_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.SEND_BUFFER_SIZE_PROP_NAME;

import java.security.KeyStore;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.qpid.configuration.QpidProperty;

public class DefaultConnectionSettingsImpl implements ConnectionSettings
{
    protected String _protocol = "tcp";

    protected String _host = "localhost";

    protected String _vhost;

    protected String _username = "guest";

    protected String _password = "guest";

    protected int _port = 5672;

    protected boolean _tcpNodelay = QpidProperty.booleanProperty(Boolean.TRUE, QPID_TCP_NODELAY_PROP_NAME,
            AMQJ_TCP_NODELAY_PROP_NAME).get();

    protected int _maxChannelCount = 32767;

    protected int _maxFrameSize = 65535;

    protected int _heartbeatInterval;

    protected int _connectTimeout = 30000;

    protected int _readBufferSize = QpidProperty.intProperty(65535, RECEIVE_BUFFER_SIZE_PROP_NAME,
            LEGACY_RECEIVE_BUFFER_SIZE_PROP_NAME).get();

    protected int _writeBufferSize = QpidProperty.intProperty(65535, SEND_BUFFER_SIZE_PROP_NAME,
            LEGACY_SEND_BUFFER_SIZE_PROP_NAME).get();;

    // SSL props
    protected boolean _useSSL;

    protected String _keyStorePath = System.getProperty("javax.net.ssl.keyStore");

    protected String _keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");

    protected String _keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType());

    protected String _keyManagerFactoryAlgorithm = QpidProperty.stringProperty(KeyManagerFactory.getDefaultAlgorithm(),
            QPID_SSL_KEY_MANAGER_FACTORY_ALGORITHM_PROP_NAME, QPID_SSL_KEY_STORE_CERT_TYPE_PROP_NAME).get();

    protected String _trustManagerFactoryAlgorithm = QpidProperty.stringProperty(
            TrustManagerFactory.getDefaultAlgorithm(), QPID_SSL_TRUST_MANAGER_FACTORY_ALGORITHM_PROP_NAME,
            QPID_SSL_TRUST_STORE_CERT_TYPE_PROP_NAME).get();

    protected String _trustStorePath = System.getProperty("javax.net.ssl.trustStore");

    protected String _trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");

    protected String _trustStoreType = System.getProperty("javax.net.ssl.trustStoreType", KeyStore.getDefaultType());

    protected String _certAlias;

    protected boolean _verifyHostname;

    // SASL props
    protected String _saslMechs = System.getProperty("qpid.sasl_mechs", null);

    protected String _saslProtocol = System.getProperty("qpid.sasl_protocol", "AMQP");

    protected String _saslServerName = System.getProperty("qpid.sasl_server_name", "localhost");

    protected boolean _useSASLEncryption;

    protected Map<String, Object> _clientProperties;

    protected DefaultConnectionSettingsImpl()
    {
    }

    protected DefaultConnectionSettingsImpl(String host, int port, String vhost, String username, String passowrd,
            boolean isSSL, String saslMechs, Map<String, Object> clientProps)
    {
        _host = host;
        _port = port;
        _vhost = vhost;
        _username = username;
        _password = passowrd;
        _useSSL = isSSL;
        _saslMechs = saslMechs;
        _clientProperties = clientProps;
    }

    protected DefaultConnectionSettingsImpl(String _protocol, String _host, String _vhost, String _username,
            String _password, int _port, boolean _tcpNodelay, int _maxChannelCount, int _maxFrameSize,
            int _heartbeatInterval, int _connectTimeout, int _readBufferSize, int _writeBufferSize, boolean _useSSL,
            String _keyStorePath, String _keyStorePassword, String _keyStoreType, String _keyManagerFactoryAlgorithm,
            String _trustManagerFactoryAlgorithm, String _trustStorePath, String _trustStorePassword,
            String _trustStoreType, String _certAlias, boolean _verifyHostname, String _saslMechs,
            String _saslProtocol, String _saslServerName, boolean _useSASLEncryption,
            Map<String, Object> _clientProperties)
    {
        super();
        this._protocol = _protocol;
        this._host = _host;
        this._vhost = _vhost;
        this._username = _username;
        this._password = _password;
        this._port = _port;
        this._tcpNodelay = _tcpNodelay;
        this._maxChannelCount = _maxChannelCount;
        this._maxFrameSize = _maxFrameSize;
        this._heartbeatInterval = _heartbeatInterval;
        this._connectTimeout = _connectTimeout;
        this._readBufferSize = _readBufferSize;
        this._writeBufferSize = _writeBufferSize;
        this._useSSL = _useSSL;
        this._keyStorePath = _keyStorePath;
        this._keyStorePassword = _keyStorePassword;
        this._keyStoreType = _keyStoreType;
        this._keyManagerFactoryAlgorithm = _keyManagerFactoryAlgorithm;
        this._trustManagerFactoryAlgorithm = _trustManagerFactoryAlgorithm;
        this._trustStorePath = _trustStorePath;
        this._trustStorePassword = _trustStorePassword;
        this._trustStoreType = _trustStoreType;
        this._certAlias = _certAlias;
        this._verifyHostname = _verifyHostname;
        this._saslMechs = _saslMechs;
        this._saslProtocol = _saslProtocol;
        this._saslServerName = _saslServerName;
        this._useSASLEncryption = _useSASLEncryption;
        this._clientProperties = _clientProperties;
    }

    @Override
    public boolean isTcpNodelay()
    {
        return _tcpNodelay;
    }

    @Override
    public int getHeartbeatInterval()
    {
        return _heartbeatInterval;
    }

    @Override
    public String getProtocol()
    {
        return _protocol;
    }

    @Override
    public String getHost()
    {
        return _host;
    }

    @Override
    public int getPort()
    {
        return _port;
    }

    @Override
    public String getVhost()
    {
        return _vhost;
    }

    @Override
    public String getUsername()
    {
        return _username;
    }

    @Override
    public String getPassword()
    {
        return _password;
    }

    @Override
    public boolean isUseSSL()
    {
        return _useSSL;
    }

    @Override
    public boolean isUseSASLEncryption()
    {
        return _useSASLEncryption;
    }

    @Override
    public String getSaslMechs()
    {
        return _saslMechs;
    }

    @Override
    public String getSaslProtocol()
    {
        return _saslProtocol;
    }

    @Override
    public String getSaslServerName()
    {
        return _saslServerName;
    }

    @Override
    public int getMaxChannelCount()
    {
        return _maxChannelCount;
    }

    @Override
    public int getMaxFrameSize()
    {
        return _maxFrameSize;
    }

    @Override
    public Map<String, Object> getClientProperties()
    {
        return _clientProperties;
    }

    @Override
    public String getKeyStorePath()
    {
        return _keyStorePath;
    }

    @Override
    public String getKeyStorePassword()
    {
        return _keyStorePassword;
    }

    @Override
    public String getKeyStoreType()
    {
        return _keyStoreType;
    }

    @Override
    public String getTrustStorePath()
    {
        return _trustStorePath;
    }

    @Override
    public String getTrustStorePassword()
    {
        return _trustStorePassword;
    }

    @Override
    public String getCertAlias()
    {
        return _certAlias;
    }

    @Override
    public boolean isVerifyHostname()
    {
        return _verifyHostname;
    }

    @Override
    public String getKeyManagerFactoryAlgorithm()
    {
        return _keyManagerFactoryAlgorithm;
    }

    @Override
    public String getTrustManagerFactoryAlgorithm()
    {
        return _trustManagerFactoryAlgorithm;
    }

    @Override
    public String getTrustStoreType()
    {
        return _trustStoreType;
    }

    @Override
    public int getConnectTimeout()
    {
        return _connectTimeout;
    }

    @Override
    public int getReadBufferSize()
    {
        return _readBufferSize;
    }

    @Override
    public int getWriteBufferSize()
    {
        return _writeBufferSize;
    }

    @Override
    public ConnectionSettings copy()
    {
        return new DefaultConnectionSettingsImpl(
                this._protocol,
                this._host,
                this._vhost,
                this._username,
                this._password,
                this._port,
                this._tcpNodelay,
                this._maxChannelCount,
                this._maxFrameSize,
                this._heartbeatInterval,
                this._connectTimeout,
                this._readBufferSize,
                this._writeBufferSize,
                this._useSSL,
                this._keyStorePath,
                this._keyStorePassword,
                this._keyStoreType,
                this._keyManagerFactoryAlgorithm,
                this._trustManagerFactoryAlgorithm,
                this._trustStorePath,
                this._trustStorePassword,
                this._trustStoreType,
                this._certAlias,
                this._verifyHostname,
                this._saslMechs,
                this._saslProtocol,
                this._saslServerName,
                this._useSASLEncryption,
                this._clientProperties);
    }
}