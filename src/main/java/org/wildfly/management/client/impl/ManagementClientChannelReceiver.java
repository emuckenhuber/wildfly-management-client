package org.wildfly.management.client.impl;

import static org.jboss.as.protocol.ProtocolLogger.ROOT_LOGGER;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;

import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.mgmt.ManagementProtocol;
import org.jboss.as.protocol.mgmt.ManagementProtocolHeader;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageInputStream;

/**
* @author Emanuel Muckenhuber
*/
public abstract class ManagementClientChannelReceiver implements Channel.Receiver {

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
            ROOT_LOGGER.tracef("%s handling incoming data", this);
            final DataInput input = new DataInputStream(message);
            final ManagementProtocolHeader header = ManagementProtocolHeader.parse(input);
            final byte type = header.getType();
            if (type == ManagementProtocol.TYPE_PING) {
                // Handle legacy ping/pong directly
                ROOT_LOGGER.tracef("Received ping on %s", this);
                ManagementConnectionImpl.handlePing(channel, header);
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

}
