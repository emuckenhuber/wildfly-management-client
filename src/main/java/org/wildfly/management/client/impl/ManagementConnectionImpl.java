package org.wildfly.management.client.impl;

import static org.jboss.as.protocol.mgmt.ProtocolUtils.expectHeader;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.client.impl.ModelControllerProtocol;
import org.jboss.as.protocol.ProtocolLogger;
import org.jboss.as.protocol.ProtocolMessages;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.mgmt.ManagementPongHeader;
import org.jboss.as.protocol.mgmt.ManagementProtocol;
import org.jboss.as.protocol.mgmt.ManagementProtocolHeader;
import org.jboss.as.protocol.mgmt.ManagementRequestHeader;
import org.jboss.as.protocol.mgmt.ManagementResponseHeader;
import org.jboss.dmr.ModelNode;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.wildfly.management.client.ManagementConnection;
import org.wildfly.management.client.OperationAttachmentCallback;
import org.xnio.FutureResult;
import org.xnio.IoUtils;
import org.xnio.Result;

/**
 * @author Emanuel Muckenhuber
 */
class ManagementConnectionImpl extends AbstractHandleableCloseable<ManagementConnectionImpl> implements ManagementConnection, CloseHandler<Channel> {

    private static final int CLOSED_FLAG = 1 << 31;

    private final Channel channel;
    private final Channel.Receiver receiver;
    private final ConcurrentMap<Integer, Request> requests = new ConcurrentHashMap<>(16, 0.75f, Runtime.getRuntime().availableProcessors());

    private volatile int state = 0;
    private volatile int count = 0;
    private static final AtomicIntegerFieldUpdater<ManagementConnectionImpl> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(ManagementConnectionImpl.class, "state");
    private static final AtomicIntegerFieldUpdater<ManagementConnectionImpl> counter = AtomicIntegerFieldUpdater.newUpdater(ManagementConnectionImpl.class, "count");

    ManagementConnectionImpl(final Channel channel, Executor executor) {
        super(executor);
        this.channel = channel;
        channel.addCloseHandler(this);
        // Create the receiver
        receiver = new ManagementClientChannelReceiver() {
            @Override
            protected void handleMessage(Channel channel, DataInput input, ManagementProtocolHeader header) {
                ManagementConnectionImpl.this.handleMessage(channel, input, header);
            }

            @Override
            protected Channel.Receiver next() {
                return this;
            }
        };
    }

    @Override
    public ModelNode execute(ModelNode operation) throws IOException {
        return execute(operation, OperationAttachmentCallback.NO_ATTACHMENTS);
    }

    @Override
    public ModelNode execute(ModelNode operation, OperationAttachmentCallback attachments) throws IOException {
        return internalExecute(operation, attachments).futureResult.getIoFuture().get();
    }

    @Override
    public Future<ModelNode> executeAsync(ModelNode operation) throws IOException {
        return executeAsync(operation, OperationAttachmentCallback.NO_ATTACHMENTS);
    }

    @Override
    public Future<ModelNode> executeAsync(ModelNode operation, OperationAttachmentCallback attachments) throws IOException {
        return internalExecute(operation, attachments);
    }

    @Override
    public void handleClose(Channel closed, IOException exception) {
        closeAsync();
    }

    protected void requestFinished() {
        int res = stateUpdater.decrementAndGet(this);
        if (res == CLOSED_FLAG) {
            closeComplete();
        }
    }

    @Override
    protected void closeAction() throws IOException {
        int res;
        do {
            res = state;
        } while (!stateUpdater.compareAndSet(this, res, res | CLOSED_FLAG));
        if (res == 0) {
            closeComplete();
        } else {

        }
    }

    protected Request internalExecute(final ModelNode operation, final OperationAttachmentCallback attachments) throws IOException {
        Request request;
        final FutureResult<ModelNode> result = new FutureResult<>();
        for (; ; ) {
            final int number = counter.incrementAndGet(this);
            request = new Request(number, attachments, result);
            if (requests.putIfAbsent(number, request) == null) {
                break;
            }
        }
        boolean ok = false;
        try {
            final ManagementRequestHeader header = new ManagementRequestHeader(ManagementProtocol.VERSION, request.id, request.id, ManagementProtocol.REQUEST_ID);
            final DataOutputStream os = new DataOutputStream(channel.writeMessage());
            try {
                header.write(os);
                operation.writeExternal(os);
                os.write(ManagementProtocol.REQUEST_END);
                os.close();
                ok = true;
            } finally {
                IoUtils.safeClose(os);
            }
            // Receive response
            channel.receiveMessage(receiver);
            return request;
        } finally {
            if (!ok) {
                requests.remove(request.id);
            }
        }
    }

    class Request extends ManagementRequestImpl implements Result<ModelNode> {

        private final int id;
        private final OperationAttachmentCallback attachments;
        private final FutureResult<ModelNode> futureResult;

        Request(final int id, final OperationAttachmentCallback attachments, final FutureResult<ModelNode> result) {
            super(result.getIoFuture());
            this.futureResult = result;
            this.attachments = attachments;
            this.id = id;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return super.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean setResult(ModelNode result) {
            if (futureResult.setResult(result)) {
                requestFinished();
                return true;
            }
            return false;
        }

        @Override
        public boolean setException(IOException exception) {
            if (futureResult.setException(exception)) {
                requestFinished();
                return true;
            }
            return false;
        }

        @Override
        public boolean setCancelled() {
            if (futureResult.setCancelled()) {
                requestFinished();
                return true;
            }
            return false;
        }
    }

    /**
     * Handle a message.
     *
     * @param channel the remoting channel
     * @param input   the data input
     * @param header  the mgmt protocol header
     */
    private void handleMessage(final Channel channel, final DataInput input, final ManagementProtocolHeader header) {
        final byte type = header.getType();
        if (type == ManagementProtocol.TYPE_RESPONSE) {
            // Handle response to local requests
            final ManagementResponseHeader response = (ManagementResponseHeader) header;
            final Request request = requests.remove(response.getResponseId());
            if (request == null) {
                ProtocolLogger.CONNECTION_LOGGER.noSuchRequest(response.getResponseId(), channel);
                safeWriteErrorResponse(channel, header, ProtocolMessages.MESSAGES.responseHandlerNotFound(response.getResponseId()));
            } else if (response.getError() != null) {
                request.setException(new IOException(response.getError()));
                requests.remove(response.getResponseId());
            } else {
                try {
                    // Handle response
                    expectHeader(input, ModelControllerProtocol.PARAM_RESPONSE);
                    final ModelNode node = new ModelNode();
                    node.readExternal(input);
                    request.setResult(node);
                    expectHeader(input, ManagementProtocol.RESPONSE_END);
                } catch (IOException e) {
                    request.setException(e);
                }
            }
        } else if (type == ManagementProtocol.TYPE_REQUEST) {
            final ManagementRequestHeader requestHeader = (ManagementRequestHeader) header;
            final Request request = requests.get(requestHeader.getBatchId());
            if (request == null) {
                safeWriteErrorResponse(channel, requestHeader, new IOException("no such request " + requestHeader.getBatchId()));
            }
            try {
                handleRequest(channel, input, request, requestHeader);
            } catch (Exception e) {
                safeWriteErrorResponse(channel, header, e);
            }
        } else {
            safeWriteErrorResponse(channel, header, new IOException("unrecognized protocol type"));
        }
    }

    void handleRequest(final Channel channel, final DataInput input, final Request request, final ManagementRequestHeader header) throws IOException {

        final byte operationID = header.getOperationId();
        switch (operationID) {
            case ModelControllerProtocol.GET_INPUTSTREAM_REQUEST:
                // Read the inputStream index
                expectHeader(input, ModelControllerProtocol.PARAM_INPUTSTREAM_INDEX);
                final int index = input.readInt();
                // Execute async
                getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final OperationAttachmentCallback attachments = request.attachments;
                            final InputStream is = attachments.getInputStream(index);
                            try {
                                final ManagementResponseHeader response = ManagementResponseHeader.create(header);
                                final OutputStream os = channel.writeMessage();
                                try {
                                    final DataOutput output = new DataOutputStream(os);
                                    // Write header
                                    response.write(output);
                                    output.writeByte(ModelControllerProtocol.PARAM_INPUTSTREAM_LENGTH);
                                    output.writeInt(attachments.getInputStreamSize(index));
                                    output.writeByte(ModelControllerProtocol.PARAM_INPUTSTREAM_CONTENTS);
                                    StreamUtils.copyStream(is, output);
                                    output.writeByte(ManagementProtocol.RESPONSE_END);
                                    os.close();
                                } finally {
                                    StreamUtils.safeClose(os);
                                }
                            } finally {
                                StreamUtils.safeClose(is);
                            }
                        } catch (Exception e) {
                            safeWriteErrorResponse(channel, header, e);
                        }
                    }
                });
                break;
            case ModelControllerProtocol.HANDLE_REPORT_REQUEST:

                expectHeader(input, ModelControllerProtocol.PARAM_MESSAGE_SEVERITY);
                final MessageSeverity severity = Enum.valueOf(MessageSeverity.class, input.readUTF());
                expectHeader(input, ModelControllerProtocol.PARAM_MESSAGE);
                final String message = input.readUTF();
                expectHeader(input, ManagementProtocol.REQUEST_END);

                // TODO do something with the message
                // Send empty response
                writeEmptyResponse(channel, header);

                break;
            default:
                throw new IOException("no such operation id");
        }
    }


    /**
     * Handle a simple ping request.
     *
     * @param channel the channel
     * @param header  the protocol header
     * @throws IOException for any error
     */
    protected static void handlePing(final Channel channel, final ManagementProtocolHeader header) throws IOException {
        final ManagementProtocolHeader response = new ManagementPongHeader(header.getVersion());
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
                ProtocolLogger.ROOT_LOGGER.tracef(ioe, "failed to write error response for %s on channel: %s", header, channel);
            }
        }
    }

    /**
     * Write an error response.
     *
     * @param channel the channel
     * @param header  the request
     * @param error   the error
     * @throws IOException
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
     * @throws IOException
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
