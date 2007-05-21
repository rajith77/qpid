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
package org.apache.qpid.server.exception;

/**
 * MessageDoesntExistException indicates that a message store cannot find a queue.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represents failure to find a queue on a message store.
 * </table>
 */
public class QueueDoesntExistException extends Exception
{
    /**
     * Constructs a new QueueDoesntExistException with the specified detail message.
     *
     * @param message the detail message.
     */
    public QueueDoesntExistException(String message)
    {
        super(message);
    }

    /**
     * Constructs a new QueueDoesntExistException with the specified detail message and
     * cause.
     *
     * @param message the detail message .
     * @param cause   the cause.
     *
     * @deprecated
     */
    public QueueDoesntExistException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructs a new QueueDoesntExistException with the specified cause.
     *
     * @param cause the cause
     *
     * @deprecated
     */
    public QueueDoesntExistException(Throwable cause)
    {
        super(cause);
    }
}
