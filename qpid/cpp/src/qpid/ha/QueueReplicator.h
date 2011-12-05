#ifndef QPID_HA_QUEUEREPLICATOR_H
#define QPID_HA_QUEUEREPLICATOR_H

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
#include "qpid/broker/Exchange.h"
#include "qpid/framing/SequenceSet.h"

namespace qpid {

namespace broker {
class Bridge;
class Link;
class Queue;
class QueueRegistry;
class SessionHandler;
class Deliverable;
}

namespace ha {

/**
 * Exchange created on a backup broker to replicate a queue on the primary.
 *
 * Puts replicated messages on the local queue, handles dequeue events.
 * Creates a ReplicatingSubscription on the primary by passing special
 * arguments to the consume command.
 *
 * THREAD SAFE.
 */
class QueueReplicator : public broker::Exchange
{
  public:
    static const std::string DEQUEUE_EVENT_KEY;
    
    QueueReplicator(boost::shared_ptr<broker::Queue> q, boost::shared_ptr<broker::Link> l);
    ~QueueReplicator();
    std::string getType() const;
    bool bind(boost::shared_ptr<broker::Queue>, const std::string&, const framing::FieldTable*);
    bool unbind(boost::shared_ptr<broker::Queue>, const std::string&, const framing::FieldTable*);
    void route(broker::Deliverable&, const std::string&, const framing::FieldTable*);
    bool isBound(boost::shared_ptr<broker::Queue>, const std::string* const, const framing::FieldTable* const);

  private:
    void initializeBridge(broker::Bridge& bridge, broker::SessionHandler& sessionHandler);

    sys::Mutex lock;
    boost::shared_ptr<broker::Queue> queue;
    boost::shared_ptr<broker::Link> link;
    framing::SequenceNumber current;
    framing::SequenceSet dequeued;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_QUEUEREPLICATOR_H*/