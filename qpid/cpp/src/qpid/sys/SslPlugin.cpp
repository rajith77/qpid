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

#include "qpid/sys/TransportFactory.h"

#include "qpid/Plugin.h"
#include "qpid/broker/Broker.h"
#include "qpid/log/Statement.h"
#include "qpid/sys/AsynchIO.h"
#include "qpid/sys/SocketTransport.h"
#include "qpid/sys/ssl/util.h"
#include "qpid/sys/ssl/SslSocket.h"

#include <boost/bind.hpp>

namespace qpid {
namespace sys {

class Timer;

using namespace qpid::sys::ssl;

struct SslServerOptions : ssl::SslOptions
{
    uint16_t port;
    bool clientAuth;
    bool nodict;
    bool multiplex;

    SslServerOptions() : port(5671),
                         clientAuth(false),
                         nodict(false),
                         multiplex(false)
    {
        addOptions()
            ("ssl-port", optValue(port, "PORT"), "Port on which to listen for SSL connections")
            ("ssl-require-client-authentication", optValue(clientAuth), 
             "Forces clients to authenticate in order to establish an SSL connection")
            ("ssl-sasl-no-dict", optValue(nodict), 
             "Disables SASL mechanisms that are vulnerable to passive dictionary-based password attacks");
    }
};

namespace {
    Socket* createServerSSLSocket(const SslServerOptions& options) {
        return new SslSocket(options.certName, options.clientAuth);
    }

    Socket* createServerSSLMuxSocket(const SslServerOptions& options) {
        return new SslMuxSocket(options.certName, options.clientAuth);
    }

    Socket* createClientSSLSocket() {
        return new SslSocket();
    }

}

// Static instance to initialise plugin
static struct SslPlugin : public Plugin {
    SslServerOptions options;
    bool nssInitialized;

    Options* getOptions() { return &options; }

    SslPlugin() : nssInitialized(false) {}
    ~SslPlugin() { if (nssInitialized) ssl::shutdownNSS(); }

    void earlyInitialize(Target& target) {
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        if (broker && !options.certDbPath.empty()) {
            broker::Broker::Options& opts = broker->getOptions();

            if (opts.port == options.port && // AMQP & AMQPS ports are the same
                opts.port != 0) {
                // The presence of this option is used to signal to the TCP
                // plugin not to start listening on the shared port. The actual
                // value cannot be configured through the command line or config
                // file (other than by setting the ports to the same value)
                // because we are only adding it after option parsing.
                options.multiplex = true;
                options.addOptions()("ssl-multiplex", optValue(options.multiplex), "Allow SSL and non-SSL connections on the same port");
            }
        }
    }

    void initialize(Target& target) {
        QPID_LOG(trace, "Initialising SSL plugin");
        broker::Broker* broker = dynamic_cast<broker::Broker*>(&target);
        // Only provide to a Broker
        if (broker) {
            if (options.certDbPath.empty()) {
                QPID_LOG(notice, "SSL plugin not enabled, you must set --ssl-cert-db to enable it.");
            } else {
                try {
                    ssl::initNSS(options, true);
                    nssInitialized = true;

                    const broker::Broker::Options& opts = broker->getOptions();
                    TransportAcceptor::shared_ptr ta;
                    SocketAcceptor* sa =
                        new SocketAcceptor(opts.tcpNoDelay, options.nodict, opts.maxNegotiateTime, broker->getTimer());
                    uint16_t port = sa->listen(opts.listenInterfaces, boost::lexical_cast<std::string>(options.port), opts.connectionBacklog,
                                               options.multiplex ?
                                                 boost::bind(&createServerSSLMuxSocket, options) :
                                                 boost::bind(&createServerSSLSocket, options));
                    if ( port!=0 ) {
                        ta.reset(sa);
                        QPID_LOG(notice, "Listening for " <<
                                        (options.multiplex ? "SSL or TCP" : "SSL") <<
                                        " connections on TCP/TCP6 port " <<
                                        port);
                    }

                    TransportConnector::shared_ptr tc(
                        new SocketConnector(opts.tcpNoDelay, options.nodict, opts.maxNegotiateTime, broker->getTimer(),
                                            &createClientSSLSocket));
                    broker->registerTransport("ssl", ta, tc, port);
                } catch (const std::exception& e) {
                    QPID_LOG(error, "Failed to initialise SSL plugin: " << e.what());
                }
            }
        }
    }
} sslPlugin;

}} // namespace qpid::sys
