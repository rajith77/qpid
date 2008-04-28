#include "RdmaIO.h"
#include "qpid/sys/Time.h"

#include <netdb.h>
#include <arpa/inet.h>

#include <vector>
#include <string>
#include <iostream>
#include <algorithm>
#include <cmath>
#include <boost/bind.hpp>

using std::vector;
using std::string;
using std::cout;
using std::cerr;
using std::copy;
using std::rand;

using qpid::sys::Poller;
using qpid::sys::Dispatcher;
using qpid::sys::AbsTime;
using qpid::sys::Duration;
using qpid::sys::TIME_SEC;
using qpid::sys::TIME_INFINITE;

// count of messages
int64_t smsgs = 0;
int64_t sbytes = 0;
int64_t rmsgs = 0;
int64_t rbytes = 0;

int outstandingwrites = 0;

int target = 1000000;
int msgsize = 200;
AbsTime startTime;
Duration sendingDuration(TIME_INFINITE);
Duration fullTestDuration(TIME_INFINITE);

vector<char> testString;

void write(Rdma::AsynchIO& aio) {
    //if ((smsgs - rmsgs) < Rdma::DEFAULT_WR_ENTRIES/2) {
        while (smsgs < target && outstandingwrites < (3*Rdma::DEFAULT_WR_ENTRIES/4)) {
            Rdma::Buffer* b = aio.getBuffer();
            std::copy(testString.begin(), testString.end(), b->bytes);
            b->dataCount = msgsize;
            aio.queueWrite(b);
            ++outstandingwrites;
            ++smsgs;
            sbytes += b->byteCount;
        }
    //}
}

void dataError(Rdma::AsynchIO&) {
    cout << "Data error:\n";
}

void data(Poller::shared_ptr p, Rdma::AsynchIO& aio, Rdma::Buffer* b) {
    ++rmsgs;
    rbytes += b->byteCount;

    // When all messages have been recvd stop
    if (rmsgs < target) {
        write(aio);
        return;
    }

    fullTestDuration = std::min(fullTestDuration, Duration(startTime, AbsTime::now()));
    if (outstandingwrites == 0)
        p->shutdown();
}

void idle(Poller::shared_ptr p, Rdma::AsynchIO& aio) {
    --outstandingwrites;
    if (smsgs < target) {
        write(aio);
        return;
    }

    sendingDuration = std::min(sendingDuration, Duration(startTime, AbsTime::now()));
    if (smsgs >= target && rmsgs >= target && outstandingwrites == 0)
        p->shutdown();
}

void connected(Poller::shared_ptr poller, Rdma::Connection::intrusive_ptr& ci) {
    cout << "Connected\n";
    Rdma::QueuePair::intrusive_ptr q = ci->getQueuePair();

    Rdma::AsynchIO* aio = new Rdma::AsynchIO(ci->getQueuePair(), msgsize,
        boost::bind(&data, poller, _1, _2),
        boost::bind(&idle, poller, _1),
        dataError);

    startTime = AbsTime::now();
    write(*aio);

    aio->start(poller);
}

void disconnected(boost::shared_ptr<Poller> p, Rdma::Connection::intrusive_ptr&) {
    cout << "Disconnected\n";
    p->shutdown();
}

void connectionError(boost::shared_ptr<Poller> p, Rdma::Connection::intrusive_ptr&) {
    cout << "Connection error\n";
    p->shutdown();
}

void rejected(boost::shared_ptr<Poller> p, Rdma::Connection::intrusive_ptr&) {
    cout << "Connection rejected\n";
    p->shutdown();
}

int main(int argc, char* argv[]) {
    vector<string> args(&argv[0], &argv[argc]);

    ::addrinfo *res;
    ::addrinfo hints = {};
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    string port = (args.size() < 3) ? "20079" : args[2];
    int n = ::getaddrinfo(args[1].c_str(), port.c_str(), &hints, &res);
    if (n<0) {
        cerr << "Can't find information for: " << args[1] << "\n";
        return 1;
    } else {
        cout << "Connecting to: " << args[1] << ":" << port <<"\n";
    }

    if (args.size() > 3)
        msgsize = atoi(args[3].c_str());
    cout << "Message size: " << msgsize << "\n";

    // Make a random message of that size
    testString.resize(msgsize);
    for (int i = 0; i < msgsize; ++i) {
        testString[i] = 32 + rand() & 0x3f;
    }

    try {
        boost::shared_ptr<Poller> p(new Poller());
        Dispatcher d(p);

        Rdma::Connector c(
            *res->ai_addr,
            boost::bind(&connected, p, _1),
            boost::bind(&connectionError, p, _1),
            boost::bind(&disconnected, p, _1),
            boost::bind(&rejected, p, _1));

        c.start(p);
        d.run();
    } catch (Rdma::Exception& e) {
        int err = e.getError();
        cerr << "Error: " << e.what() << "(" << err << ")\n";
    }

    cout
        << "Sent: " << smsgs
        << "msgs (" << sbytes
        << "bytes) in: " << double(sendingDuration)/TIME_SEC
        << "s: " << double(smsgs)*TIME_SEC/sendingDuration
        << "msgs/s(" << double(sbytes)*TIME_SEC/sendingDuration
        << "bytes/s)\n";
    cout
        << "Recd: " << rmsgs
        << "msgs (" << rbytes
        << "bytes) in: " << double(fullTestDuration)/TIME_SEC
        << "s: " << double(rmsgs)*TIME_SEC/fullTestDuration
        << "msgs/s(" << double(rbytes)*TIME_SEC/fullTestDuration
        << "bytes/s)\n";

}
