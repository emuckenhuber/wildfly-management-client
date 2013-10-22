/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.management.client.impl;

import static org.wildfly.management.client.ManagementClientLogger.ROOT_LOGGER;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;
import org.wildfly.management.client.ManagementClientLogger;

/**
 * @author Emanuel Muckenhuber
 */
abstract class ManagementClientChannelReceiver implements Channel.Receiver {

    /**
     * Process a received message.
     *
     * @param channel the channel
     * @param input   the data input
     * @param header  the protocol header
     */
    protected abstract void handleMessage(final Channel channel, final DataInput input, final ManagementProtocolHeader header);

    /**
     * Get the next receiver in the chain.
     *
     * @return the next receiver
     */
    protected abstract Channel.Receiver next();

    @Override
    public void handleMessage(Channel channel, MessageInputStream message) {
        try {
            ManagementClientLogger.ROOT_LOGGER.tracef("%s handling incoming data", this);
            final DataInput input = new DataInputStream(message);
            final ManagementProtocolHeader header = ManagementProtocolHeader.parse(input);
            final byte type = header.getType();
            if (type == ManagementProtocol.TYPE_PING) {
                // Handle legacy ping/pong directly
                ROOT_LOGGER.tracef("Received ping on %s", this);
                handlePing(channel, header);
            } else if (type == ManagementProtocol.TYPE_PONG) {
                // Nothing to do here
                ROOT_LOGGER.tracef("Received on on %s", this);
            } else if (type == ManagementProtocol.TYPE_BYE_BYE) {
                // Close the channel
                ROOT_LOGGER.tracef("Received bye bye on %s, closing", this);
            } else {
                // Handle a message
                handleMessage(channel, input, header);
            }
            message.close();
        } catch (IOException e) {
            handleError(channel, e);
        } catch (Exception e) {
            handleError(channel, new IOException(e));
        } finally {
            StreamUtils.safeClose(message);
            ROOT_LOGGER.tracef("%s done handling incoming data", this);
        }
        // Receive next message
        final Channel.Receiver next = next();
        if (next != null) {
            channel.receiveMessage(next);
        }
    }

    @Override
    public void handleError(Channel channel, IOException error) {
        StreamUtils.safeClose(channel);
    }

    @Override
    public void handleEnd(Channel channel) {
        StreamUtils.safeClose(channel);
    }


    /**
     * Handle a simple ping request.
     *
     * @param channel the channel
     * @param header  the protocol header
     * @throws java.io.IOException for any error
     */
    protected static void handlePing(final Channel channel, final ManagementProtocolHeader header) throws IOException {
        final ManagementProtocolHeader response = new ManagementProtocolHeader.ManagementPongHeader(header.getVersion());
        final DataOutputStream output = new DataOutputStream(channel.writeMessage());
        try {
            response.write(output);
            output.close();
        } finally {
            StreamUtils.safeClose(output);
        }
    }

    /**
     * Safe write error response.
     *
     * @param channel the channel
     * @param header  the request header
     * @param error   the exception
     */
    protected static void safeWriteErrorResponse(final Channel channel, final ManagementProtocolHeader header, final Exception error) {
        if (header.getType() == ManagementProtocol.TYPE_REQUEST) {
            try {
                writeErrorResponse(channel, (ManagementRequestHeader) header, error);
            } catch (IOException ioe) {
                ROOT_LOGGER.tracef(ioe, "failed to write error response for %s on channel: %s", header, channel);
            }
        }
    }

    /**
     * Write an error response.
     *
     * @param channel the channel
     * @param header  the request
     * @param error   the error
     * @throws java.io.IOException
     */
    protected static void writeErrorResponse(final Channel channel, final ManagementRequestHeader header, final Exception error) throws IOException {
        final ManagementResponseHeader response = ManagementResponseHeader.create(header, error);
        final DataOutputStream output = new DataOutputStream(channel.writeMessage());
        try {
            response.write(output);
            output.close();
        } finally {
            StreamUtils.safeClose(output);
        }
    }

    /**
     * Write an empty response.
     *
     * @param channel the channel
     * @param header  the request
     * @throws java.io.IOException
     */
    protected static void writeEmptyResponse(final Channel channel, final ManagementRequestHeader header) throws IOException {
        final ManagementResponseHeader response = ManagementResponseHeader.create(header);
        final DataOutputStream output = new DataOutputStream(channel.writeMessage());
        try {
            response.write(output);
            output.write(ManagementProtocol.REQUEST_END);
            output.close();
        } finally {
            StreamUtils.safeClose(output);
        }
    }

}
