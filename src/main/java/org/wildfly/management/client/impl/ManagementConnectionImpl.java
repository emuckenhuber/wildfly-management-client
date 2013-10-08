package org.wildfly.management.client.impl;

import static org.jboss.as.protocol.mgmt.ProtocolUtils.expectHeader;
import static org.wildfly.management.client.impl.ManagementClientChannelReceiver.safeWriteErrorResponse;

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
import org.jboss.as.protocol.mgmt.ManagementProtocol;
import org.jboss.as.protocol.mgmt.ManagementProtocolHeader;
import org.jboss.as.protocol.mgmt.ManagementRequestHeader;
import org.jboss.as.protocol.mgmt.ManagementResponseHeader;
import org.jboss.dmr.ModelNode;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.wildfly.management.client.ManagementConnection;
import org.wildfly.management.client.NotificationFilter;
import org.wildfly.management.client.NotificationHandler;
import org.wildfly.management.client.OperationAttachmentCallback;
import org.xnio.FutureResult;
import org.xnio.IoUtils;

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
    public ModelNode execute(final ModelNode operation) throws IOException {
        return execute(operation, OperationAttachmentCallback.NO_ATTACHMENTS);
    }

    @Override
    public ModelNode execute(final ModelNode operation, final OperationAttachmentCallback attachments) throws IOException {
        return internalExecute(operation, attachments).futureResult.getIoFuture().get();
    }

    @Override
    public Future<ModelNode> executeAsync(final ModelNode operation) throws IOException {
        return executeAsync(operation, OperationAttachmentCallback.NO_ATTACHMENTS);
    }

    @Override
    public Future<ModelNode> executeAsync(final ModelNode operation, final OperationAttachmentCallback attachments) throws IOException {
        return internalExecute(operation, attachments);
    }

    @Override
    public NotificationRegistration registerNotificationHandler(final ModelNode address, final NotificationHandler handler, final NotificationFilter filter) {


        return null;  //To change body of implemented methods use File | Settings | File Templates.
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
            for (final Request request : requests.values()) {
                request.asyncCancel();
            }
        }
    }

    protected void increaseRequestCount() throws IOException {
        int old;
        do {
            old = stateUpdater.get(this);
            if ((old & CLOSED_FLAG) != 0) {
                throw new IOException("connection closed");
            }
        } while (! stateUpdater.compareAndSet(this, old, old + 1));
    }

    protected BaseRequest internalExecute(final ModelNode operation, final OperationAttachmentCallback attachments) throws IOException {
        BaseRequest request;
        final FutureResult<ModelNode> result = new FutureResult<>();
        for (;;) {
            final int number = counter.incrementAndGet(this);
            request = new BaseRequest(number, attachments, result);
            if (requests.putIfAbsent(number, request) == null) {
                break;
            }
        }
        boolean ok = false;
        try {
            increaseRequestCount();
            final ManagementRequestHeader header = new ManagementRequestHeader(ManagementProtocol.VERSION, request.id, request.id, ModelControllerProtocol.EXECUTE_ASYNC_CLIENT_REQUEST);
            final int inputStreamLength = attachments != null ? attachments.getNumberOfAttachedStreams() : 0;
            final DataOutputStream os = new DataOutputStream(channel.writeMessage());
            try {
                header.write(os);
                os.write(ModelControllerProtocol.PARAM_OPERATION);
                operation.writeExternal(os);
                os.write(ModelControllerProtocol.PARAM_INPUTSTREAMS_LENGTH);
                os.writeInt(inputStreamLength);
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

    protected void cancelRequest(final BaseRequest original, final int batchID) {
        try {
            CancelRequest request;
            for (;;) {
                final int number = counter.incrementAndGet(this);
                request = new CancelRequest(number, original);
                if (requests.putIfAbsent(number, request) == null) {
                    break;
                }
            }
            boolean ok = false;
            try {
                increaseRequestCount();
                final ManagementRequestHeader header = new ManagementRequestHeader(ManagementProtocol.VERSION, request.requestID, batchID, ModelControllerProtocol.CANCEL_ASYNC_REQUEST);
                final DataOutputStream os = new DataOutputStream(channel.writeMessage());
                try {
                    header.write(os);
                    os.write(ManagementProtocol.REQUEST_END);
                    os.close();
                    ok = true;
                } finally {
                    IoUtils.safeClose(os);
                }
            } finally {
                if (!ok) {
                    requests.remove(request.requestID);
                }
            }
        } catch (IOException e) {
            // Maybe cancel the request all the time?
            if ((stateUpdater.get(this) & CLOSED_FLAG) != 0) {
                original.setCancelled();
            }
        }
    }

    interface Request {

        OperationAttachmentCallback getAttachments();

        void handleResponse(ManagementResponseHeader header, DataInput input) throws IOException;
        void handleFailure(IOException e);
        void asyncCancel();

    }

    class BaseRequest extends ManagementRequestImpl implements Request {

        private final int id;
        private final OperationAttachmentCallback attachments;
        private final FutureResult<ModelNode> futureResult;
        private boolean cancelled = false;

        BaseRequest(final int id, final OperationAttachmentCallback attachments, final FutureResult<ModelNode> result) {
            super(result.getIoFuture());
            this.futureResult = result;
            this.attachments = attachments;
            this.id = id;
        }

        @Override
        public OperationAttachmentCallback getAttachments() {
            return attachments;
        }

        @Override
        public void handleResponse(ManagementResponseHeader header, DataInput input) throws IOException {
            // Handle response
            expectHeader(input, ModelControllerProtocol.PARAM_RESPONSE);
            final ModelNode node = new ModelNode();
            node.readExternal(input);
            if (futureResult.setResult(node)) {
                requestFinished();
            }
            expectHeader(input, ManagementProtocol.RESPONSE_END);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            asyncCancel();
            futureResult.getIoFuture().await();
            return super.cancel(mayInterruptIfRunning);
        }

        @Override
        public void asyncCancel() {
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                cancelRequest(this, id);
            }
        }

        @Override
        public void handleFailure(IOException exception) {
            if (futureResult.setException(exception)) {
                requestFinished();
            }
        }

        protected boolean setCancelled() {
            if (futureResult.setCancelled()) {
                requestFinished();
                return true;
            }
            return false;
        }
    }

    class CancelRequest implements Request {

        private final BaseRequest toCancel;
        private final int requestID;
        CancelRequest(final int requestID, final BaseRequest toCancel) {
            this.toCancel = toCancel;
            this.requestID = requestID;
        }

        @Override
        public OperationAttachmentCallback getAttachments() {
            return OperationAttachmentCallback.NO_ATTACHMENTS;
        }

        @Override
        public void handleFailure(IOException e) {
            toCancel.handleFailure(e);
            requestFinished();
        }

        @Override
        public void handleResponse(ManagementResponseHeader header, DataInput input) throws IOException {
            System.out.println("cancel complete");
            toCancel.setCancelled();
            requestFinished();
        }

        @Override
        public void asyncCancel() {
            // Nothing here.s
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
                request.handleFailure(new IOException(response.getError()));
            } else {
                try {
                    request.handleResponse(response, input);
                } catch (IOException e) {
                    request.handleFailure(e);
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
                            final OperationAttachmentCallback attachments = request.getAttachments();
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
                ManagementClientChannelReceiver.writeEmptyResponse(channel, header);

                break;
            default:
                throw new IOException("no such operation id");
        }
    }

}
