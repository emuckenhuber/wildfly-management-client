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

import static org.wildfly.management.client.impl.ManagementClientChannelReceiver.safeWriteErrorResponse;
import static org.wildfly.management.client.impl.ManagementProtocol.CANCEL_ASYNC_REQUEST;
import static org.wildfly.management.client.impl.ManagementProtocol.EXECUTE_ASYNC_CLIENT_REQUEST;
import static org.wildfly.management.client.impl.ManagementProtocol.GET_INPUTSTREAM_REQUEST;
import static org.wildfly.management.client.impl.ManagementProtocol.HANDLE_NOTIFICATION_REQUEST;
import static org.wildfly.management.client.impl.ManagementProtocol.HANDLE_REPORT_REQUEST;
import static org.wildfly.management.client.impl.ManagementProtocol.PARAM_COMMIT;
import static org.wildfly.management.client.impl.ManagementProtocol.REGISTER_NOTIFICATION_HANDLER_REQUEST;
import static org.wildfly.management.client.impl.ManagementProtocol.UNREGISTER_NOTIFICATION_HANDLER_REQUEST;

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

import org.jboss.dmr.ModelNode;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.wildfly.management.client.ManagementClientLogger;
import org.wildfly.management.client.ManagementClientMessages;
import org.wildfly.management.client.ManagementConnection;
import org.wildfly.management.client.MessageSeverity;
import org.wildfly.management.client.Notification;
import org.wildfly.management.client.NotificationFilter;
import org.wildfly.management.client.NotificationHandler;
import org.wildfly.management.client.OperationAttachments;
import org.xnio.FutureResult;
import org.xnio.IoUtils;

/**
 * The management connection implementation.
 *
 * @author Emanuel Muckenhuber
 */
class ManagementConnectionImpl extends AbstractHandleableCloseable<ManagementConnectionImpl> implements ManagementConnection, CloseHandler<Channel> {

    private final Channel channel;
    private final Channel.Receiver receiver;
    private final ConcurrentMap<Integer, ManagementRequest> requests = new ConcurrentHashMap<>(16, 0.75f, Runtime.getRuntime().availableProcessors());
    private final ConcurrentMap<Integer, NotificationExecutionContext> notificationHandlers = new ConcurrentHashMap<>(16, 0.75f, Runtime.getRuntime().availableProcessors());

    private volatile int state = 0;
    private volatile int count = 0;
    private static final int CLOSED_FLAG = 1 << 31;
    private static final AtomicIntegerFieldUpdater<ManagementConnectionImpl> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(ManagementConnectionImpl.class, "state");
    private static final AtomicIntegerFieldUpdater<ManagementConnectionImpl> counter = AtomicIntegerFieldUpdater.newUpdater(ManagementConnectionImpl.class, "count");

    ManagementConnectionImpl(final Channel channel, final Executor executor) {
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
        // Start receiving messages, maybe this should only happen after the first request?
        channel.receiveMessage(receiver);
    }

    @Override
    public ModelNode execute(final ModelNode operation) throws IOException {
        return execute(operation, OperationAttachments.NO_ATTACHMENTS);
    }

    @Override
    public ModelNode execute(final ModelNode operation, final OperationAttachments attachments) throws IOException {
        return internalExecute(operation, attachments).futureResult.getIoFuture().get();
    }

    @Override
    public Future<ModelNode> executeAsync(final ModelNode operation) throws IOException {
        return executeAsync(operation, OperationAttachments.NO_ATTACHMENTS);
    }

    @Override
    public Future<ModelNode> executeAsync(final ModelNode operation, final OperationAttachments attachments) throws IOException {
        return internalExecute(operation, attachments);
    }

    private ExecuteRequest internalExecute(final ModelNode operation, final OperationAttachments attachments) throws IOException {
        ExecuteRequest request;
        final FutureResult<ModelNode> result = new FutureResult<>();
        for (;;) {
            final int requestID = counter.incrementAndGet(this);
            request = new ExecuteRequest(requestID, operation, attachments, result);
            if (requests.putIfAbsent(requestID, request) == null) {
                if (!notificationHandlers.containsKey(requestID)) {
                    break;
                }
                requests.remove(requestID);
            }
        }
        writeRequest(request, request.id);
        return request;
    }

    @Override
    public NotificationRegistration registerNotificationHandler(final ModelNode address, final NotificationHandler handler, final NotificationFilter filter) {
        RegisterNotificationHandler request;
        for (;;) {
            final int requestID = counter.incrementAndGet(this);
            final NotificationExecutionContext context = new NotificationExecutionContext(handler, filter);
            request = new RegisterNotificationHandler(requestID, address, context);
            if (requests.putIfAbsent(requestID, request) == null) {
                if (!notificationHandlers.containsKey(requestID)) {
                    break;
                }
                requests.remove(requestID);
            }
        }
        try {
            writeRequest(request, request.getOperationId());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final int batchID = request.getOperationId();
        final NotificationRegistration registration = new NotificationRegistration() {
            @Override
            public void unregister() throws IOException {
                UnregisterNotificationHandler request;
                int requestID;
                for (;;) {
                    requestID = counter.incrementAndGet(ManagementConnectionImpl.this);
                    request = new UnregisterNotificationHandler(batchID);
                    if (requests.putIfAbsent(requestID, request) == null) {
                        if (!notificationHandlers.containsKey(requestID)) {
                            break;
                        }
                        requests.remove(requestID);
                    }
                }
                writeRequest(request, requestID);
                // Wait until the notification listener is unregistered
                request.futureResult.getIoFuture().await();

            }
        };
        // Wait until the notification listener is registered
        request.futureResult.getIoFuture().await();
        return registration;
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
            for (final ManagementRequest request : requests.values()) {
                request.asyncCancel();
            }
        }
    }

    @Override
    protected void closeComplete() {
        try {
            channel.closeAsync(); // make sure the channel is closed
            notificationHandlers.clear(); // clear the notification handlers
        } finally {
            super.closeComplete();
        }
    }

    protected void increaseRequestCount() throws IOException {
        int old;
        do {
            old = stateUpdater.get(this);
            if ((old & CLOSED_FLAG) != 0) {
                throw new IOException("connection closed");
            }
        } while (!stateUpdater.compareAndSet(this, old, old + 1));
    }

    protected void writeRequest(final ManagementRequest request, int requestId) throws IOException {
        boolean ok = false;
        try {
            increaseRequestCount();
            final ManagementRequestHeader header = new ManagementRequestHeader(ManagementProtocol.VERSION, requestId, request.getOperationId(), request.getRequestType());
            final DataOutputStream os = new DataOutputStream(channel.writeMessage());
            try {
                header.write(os);
                request.writeRequest(os);
                os.close();
                ok = true;
            } finally {
                IoUtils.safeClose(os);
            }
        } catch (IOException e) {
            request.handleFailure(e);
            throw e;
        } catch (Exception e) {
            final IOException ex = new IOException(e);
            request.handleFailure(ex);
            throw ex;
        } finally {
            if (!ok) {
                requests.remove(requestId);
            }
        }
    }

    protected void cancelRequest(final ExecuteRequest original) {
        try {
            CancelRequest request;
            for (;;) {
                final int requestID = counter.incrementAndGet(this);
                request = new CancelRequest(requestID, original);
                if (requests.putIfAbsent(requestID, request) == null) {
                    if (!notificationHandlers.containsKey(requestID)) {
                        break;
                    }
                    requests.remove(requestID);
                }
            }
            writeRequest(request, request.requestID);
        } catch (IOException e) {
            // Maybe cancel the request all the time?
            if ((stateUpdater.get(this) & CLOSED_FLAG) != 0) {
                original.setCancelled();
            }
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
            final ManagementRequest request = requests.remove(response.getResponseId());
            if (request == null) {
                ManagementClientLogger.ROOT_LOGGER.noSuchRequest(response.getResponseId(), channel);
                safeWriteErrorResponse(channel, header, ManagementClientMessages.MESSAGES.responseHandlerNotFound(response.getResponseId()));
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
            try {
                handleRemoteRequest(channel, input, requestHeader);
            } catch (Exception e) {
                safeWriteErrorResponse(channel, header, e);
            }
        } else {
            safeWriteErrorResponse(channel, header, new IOException("unrecognized protocol type"));
        }
    }

    /**
     * Handle a remote request.
     *
     * @param channel the channel
     * @param input   the input
     * @param header
     * @throws IOException
     */
    void handleRemoteRequest(final Channel channel, final DataInput input, final ManagementRequestHeader header) throws IOException {

        final byte operationID = header.getOperationId();
        switch (operationID) {
            case GET_INPUTSTREAM_REQUEST:
                final ManagementRequest request = requests.get(header.getBatchId());
                if (request == null) {
                    throw new IOException("no request " + header.getBatchId());
                }
                // Read the inputStream index
                StreamUtils.expectHeader(input, ManagementProtocol.PARAM_INPUTSTREAM_INDEX);
                final int index = input.readInt();
                // Execute async
                getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final OperationAttachments attachments = request.getAttachments();
                            final InputStream is = attachments.getInputStream(index);
                            try {
                                final ManagementResponseHeader response = ManagementResponseHeader.create(header);
                                final OutputStream os = channel.writeMessage();
                                try {
                                    final DataOutput output = new DataOutputStream(os);
                                    // Write header
                                    response.write(output);
                                    output.writeByte(ManagementProtocol.PARAM_INPUTSTREAM_LENGTH);
                                    output.writeInt(attachments.getInputStreamSize(index));
                                    output.writeByte(ManagementProtocol.PARAM_INPUTSTREAM_CONTENTS);
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
            case HANDLE_NOTIFICATION_REQUEST:
                final ModelNode notif = new ModelNode();
                notif.readExternal(input);
                final Notification notification = Notification.fromModelNode(notif);
                final NotificationExecutionContext context = notificationHandlers.get(header.getBatchId());
                try {
                    final NotificationFilter filter = context.getFilter();
                    if (filter.isNotificationEnabled(notification)) {
                        NotificationHandler notificationHandler = context.getHandler();
                        notificationHandler.handleNotification(notification);
                    }
                } catch (Exception e) {
                    ManagementClientChannelReceiver.writeErrorResponse(channel, header, e);
                }
                ManagementClientChannelReceiver.writeEmptyResponse(channel, header);
                break;
            case HANDLE_REPORT_REQUEST:

                StreamUtils.expectHeader(input, ManagementProtocol.PARAM_MESSAGE_SEVERITY);
                final MessageSeverity severity = Enum.valueOf(MessageSeverity.class, input.readUTF());
                StreamUtils.expectHeader(input, ManagementProtocol.PARAM_MESSAGE);
                final String message = input.readUTF();
                StreamUtils.expectHeader(input, ManagementProtocol.REQUEST_END);

                // TODO do something with the message
                // Send empty response
                ManagementClientChannelReceiver.writeEmptyResponse(channel, header);

                break;
            default:
                throw new IOException("no such operation id");
        }
    }

    class ExecuteRequest extends ManagementRequestFutureImpl implements ManagementRequest {

        private final int id;
        private final ModelNode operation;
        private final OperationAttachments attachments;
        private final FutureResult<ModelNode> futureResult;
        private boolean cancelled = false;

        ExecuteRequest(final int id, final ModelNode operation, final OperationAttachments attachments, final FutureResult<ModelNode> result) {
            super(result.getIoFuture());
            this.futureResult = result;
            this.attachments = attachments;
            this.operation = operation;
            this.id = id;
        }

        @Override
        public void writeRequest(DataOutput os) throws IOException {
            final int inputStreamLength = attachments != null ? attachments.getNumberOfAttachedStreams() : 0;
            os.write(ManagementProtocol.PARAM_OPERATION);
            operation.writeExternal(os);
            os.write(ManagementProtocol.PARAM_INPUTSTREAMS_LENGTH);
            os.writeInt(inputStreamLength);
            os.write(ManagementProtocol.REQUEST_END);
        }

        @Override
        public byte getRequestType() {
            return EXECUTE_ASYNC_CLIENT_REQUEST;
        }

        @Override
        public int getOperationId() {
            return id;
        }

        @Override
        public OperationAttachments getAttachments() {
            return attachments;
        }

        @Override
        public synchronized void handleResponse(ManagementResponseHeader header, DataInput input) throws IOException {
            // Handle response
            StreamUtils.expectHeader(input, ManagementProtocol.PARAM_RESPONSE);
            final ModelNode node = new ModelNode();
            node.readExternal(input);
            synchronized (this) {
                boolean finished;
                if (cancelled) {
                    finished = futureResult.setCancelled();
                } else {
                    finished = futureResult.setResult(node);
                }
                if (finished) {
                    requestFinished();
                }
            }
            StreamUtils.expectHeader(input, ManagementProtocol.RESPONSE_END);
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
                cancelRequest(this);
            }
        }

        @Override
        public void handleFailure(IOException exception) {
            if (futureResult.setException(exception)) {
                exception.printStackTrace();
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

    class CancelRequest implements ManagementRequest {

        private final ExecuteRequest toCancel;
        private final int requestID;

        CancelRequest(final int requestID, final ExecuteRequest toCancel) {
            this.toCancel = toCancel;
            this.requestID = requestID;
        }

        @Override
        public int getOperationId() {
            return toCancel.id;
        }

        @Override
        public byte getRequestType() {
            return CANCEL_ASYNC_REQUEST;
        }

        @Override
        public void writeRequest(DataOutput output) throws IOException {
            // nothing
        }

        @Override
        public OperationAttachments getAttachments() {
            return OperationAttachments.NO_ATTACHMENTS;
        }

        @Override
        public void handleFailure(IOException exception) {
            toCancel.handleFailure(exception); // maybe just log?
            requestFinished();
        }

        @Override
        public void handleResponse(ManagementResponseHeader header, DataInput input) throws IOException {
            // toCancel.setCancelled() // wait for original response
            requestFinished();
        }

        @Override
        public void asyncCancel() {
            // Nothing here.
        }
    }

    abstract class AbstractNotificationHandler implements ManagementRequest {

        final FutureResult<ModelNode> futureResult = new FutureResult<>();
        private final int requestId;

        protected AbstractNotificationHandler(int requestId) {
            this.requestId = requestId;
        }

        abstract void completed();

        @Override
        public OperationAttachments getAttachments() {
            return OperationAttachments.NO_ATTACHMENTS;
        }

        @Override
        public int getOperationId() {
            return requestId;
        }

        @Override
        public void handleResponse(ManagementResponseHeader header, DataInput input) throws IOException {
            if (futureResult.setResult(null)) {
                completed();
                requestFinished();
            }
        }

        @Override
        public void handleFailure(IOException exception) {
            if (futureResult.setException(exception)) {
                requestFinished();
            }
        }

        @Override
        public void asyncCancel() {
            // don't wait for the remote response just cancel
            if (futureResult.setCancelled()) {
                requestFinished();
            }
        }
    }

    class RegisterNotificationHandler extends AbstractNotificationHandler {

        private final ModelNode address;
        private final NotificationExecutionContext handler;

        RegisterNotificationHandler(final int requestId, final ModelNode address, final NotificationExecutionContext handler) {
            super(requestId);
            this.address = address;
            this.handler = handler;
        }

        @Override
        public byte getRequestType() {
            return REGISTER_NOTIFICATION_HANDLER_REQUEST;
        }

        @Override
        void completed() {
            notificationHandlers.put(getOperationId(), handler);
        }

        @Override
        public void writeRequest(DataOutput output) throws IOException {
            address.writeExternal(output);
        }
    }

    class UnregisterNotificationHandler extends AbstractNotificationHandler {

        UnregisterNotificationHandler(int batchID) {
            super(batchID);
        }

        @Override
        void completed() {
            notificationHandlers.remove(getOperationId());
        }

        @Override
        public byte getRequestType() {
            return UNREGISTER_NOTIFICATION_HANDLER_REQUEST;
        }

        @Override
        public void writeRequest(DataOutput output) throws IOException {
            //
        }

    }

    private class NotificationExecutionContext {
        private final NotificationHandler handler;
        private final NotificationFilter filter;

        private NotificationExecutionContext(NotificationHandler handler, NotificationFilter filter) {
            this.handler = handler;
            this.filter = filter;
        }

        public NotificationHandler getHandler() {
            return handler;
        }

        public NotificationFilter getFilter() {
            return filter;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            NotificationExecutionContext that = (NotificationExecutionContext) o;

            if (filter != null ? !filter.equals(that.filter) : that.filter != null) return false;
            if (handler != null ? !handler.equals(that.handler) : that.handler != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = handler != null ? handler.hashCode() : 0;
            result = 31 * result + (filter != null ? filter.hashCode() : 0);
            return result;
        }
    }

}
