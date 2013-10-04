package org.wildfly.management.client.impl;

import static org.xnio.IoFuture.HandlingNotifier;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.jboss.as.controller.client.ControllerClientLogger;
import org.jboss.as.controller.client.ControllerClientMessages;
import org.jboss.as.protocol.ProtocolMessages;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.wildfly.management.client.ManagementClient;
import org.wildfly.management.client.ManagementConnection;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * @author Emanuel Muckenhuber
 */
public class ManagementClientImpl extends AbstractHandleableCloseable<ManagementClientImpl> implements ManagementClient {

    private static final String CHANNEL_TYPE = "management";
    private static final int CLOSED_FLAG = 1 << 31;

    private volatile int state = 0;
    private static final AtomicIntegerFieldUpdater<ManagementClientImpl> stateUpdater = AtomicIntegerFieldUpdater.newUpdater(ManagementClientImpl.class, "state");
    private final Set<ManagementConnectionImpl> connections = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<ManagementConnectionImpl, Boolean>()));

    private final Endpoint endpoint;
    private final OptionMap options;
    private final StackTraceElement[] allocationStackTrace;
    private final CloseHandler<ManagementConnectionImpl> connectionCloseHandler = new CloseHandler<ManagementConnectionImpl>() {
        @Override
        public void handleClose(ManagementConnectionImpl closed, IOException exception) {
            connectionClosed(closed);
        }
    };

    protected ManagementClientImpl(final Endpoint endpoint, final OptionMap options, final ExecutorService executor) {
        super(executor);
        this.options = options;
        this.endpoint = endpoint;
        allocationStackTrace = Thread.currentThread().getStackTrace();
    }

    @Override
    public Future<ManagementConnection> openConnection() throws IOException {
        final IoFuture<ManagementConnection> result = null;

        return null;
    }

    ManagementConnection internalConnectSync(final String protocol, final SocketAddress bindAddress, final SocketAddress destination,
                                     final OptionMap connectOptions, final CallbackHandler callbackHandler,
                                     final SSLContext sslContext, final int connectionTimeout) throws IOException {

        final CallbackHandler actualHandler = callbackHandler != null ? callbackHandler : new AnonymousCallbackHandler();
        final WrapperCallbackHandler wrapperHandler = new WrapperCallbackHandler(actualHandler);
        final IoFuture<ManagementConnection> future = internalOpenConnection(protocol, bindAddress, destination, connectOptions, wrapperHandler, sslContext);
        long timeoutMillis = connectionTimeout;
        IoFuture.Status status = future.await(timeoutMillis, TimeUnit.MILLISECONDS);
        while (status == IoFuture.Status.WAITING) {
            if (wrapperHandler.isInCall()) {
                // If there is currently an interaction with the user just wait again.
                status = future.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } else {
                long lastInteraction = wrapperHandler.getCallFinished();
                if (lastInteraction > 0) {
                    long now = System.currentTimeMillis();
                    long timeSinceLast = now - lastInteraction;
                    if (timeSinceLast < timeoutMillis) {
                        // As this point we are setting the timeout based on the time of the last interaction
                        // with the user, if there is any time left we will wait for that time but dont wait for
                        // a full timeout.
                        status = future.await(timeoutMillis - timeSinceLast, TimeUnit.MILLISECONDS);
                    } else {
                        status = null;
                    }
                } else {
                    status = null; // Just terminate status processing.
                }
            }
        }

        if (status == IoFuture.Status.DONE) {
            return future.get();
        }
        if (status == IoFuture.Status.FAILED) {
            throw ProtocolMessages.MESSAGES.failedToConnect(null, future.getException());
        }
        future.cancel();
        throw ProtocolMessages.MESSAGES.couldNotConnect(null);

    }

    IoFuture<ManagementConnection> internalOpenConnection(final String protocol, final SocketAddress bindAddress, final SocketAddress destination,
                                                          final OptionMap connectOptions, final CallbackHandler callbackHandler, final SSLContext sslContext) throws IOException {
        int old;
        do {
            old = stateUpdater.get(this);
            if ((old & CLOSED_FLAG) != 0) {
                throw new IOException("client is closed");
            }
        } while (!stateUpdater.compareAndSet(this, old, old + 1));
        final FutureResult<ManagementConnection> result = new FutureResult<>();
        boolean ok = false;
        try {
            final IoFuture<Connection> connectionFuture = endpoint.connect(protocol, bindAddress, destination, connectOptions, callbackHandler, sslContext);
            connectionFuture.addNotifier(new HandlingNotifier<Connection, Void>() {
                @Override
                public void handleCancelled(Void attachment) {
                    connectionClosed(null);
                    result.setCancelled();
                }

                @Override
                public void handleFailed(IOException exception, Void attachment) {
                    connectionClosed(null);
                    result.setException(exception);
                }

                @Override
                public void handleDone(Connection connection, Void attachment) {
                    final IoFuture<Channel> channelFuture = connection.openChannel(CHANNEL_TYPE, connectOptions);
                    channelFuture.addNotifier(new HandlingNotifier<Channel, Void>() {
                        @Override
                        public void handleCancelled(Void attachment) {
                            connectionClosed(null);
                            result.setCancelled();
                        }

                        @Override
                        public void handleFailed(IOException exception, Void attachment) {
                            connectionClosed(null);
                            result.setException(exception);
                        }

                        @Override
                        public void handleDone(Channel data, Void attachment) {
                            final ManagementConnectionImpl connection = new ManagementConnectionImpl(data, getExecutor());
                            connections.add(connection);
                            connection.addCloseHandler(connectionCloseHandler);
                        }
                    }, null);
                }
            }, null);
            ok = true;
        } finally {
            if (!ok) {
                connectionClosed(null);
            }
        }
        return result.getIoFuture();
    }

    protected void connectionClosed(final ManagementConnectionImpl connection) {
        int res = stateUpdater.decrementAndGet(this);
        if (res == CLOSED_FLAG) {
            closeComplete();
        }
        if (connection != null) {
            connections.remove(connection);
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
            for (Object connection : connections.toArray()) {
                ((ManagementConnectionImpl) connection).closeAsync();
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (isOpen()) {
                // Create the leak description
                final Throwable t = ControllerClientMessages.MESSAGES.controllerClientNotClosed();
                t.setStackTrace(allocationStackTrace);
                ControllerClientLogger.ROOT_LOGGER.leakedControllerClient(t);
                // Close
                StreamUtils.safeClose(this);
            }
        } finally {
            super.finalize();
        }
    }

    private static final class WrapperCallbackHandler implements CallbackHandler {

        private volatile boolean inCall = false;

        private volatile long callFinished = -1;

        private final CallbackHandler wrapped;

        WrapperCallbackHandler(final CallbackHandler toWrap) {
            this.wrapped = toWrap;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            inCall = true;
            try {
                wrapped.handle(callbacks);
            } finally {
                // Set the time first so if a read is made between these two calls it will say inCall=true until
                // callFinished is set.
                callFinished = System.currentTimeMillis();
                inCall = false;
            }
        }

        boolean isInCall() {
            return inCall;
        }

        long getCallFinished() {
            return callFinished;
        }
    }

    private static final class AnonymousCallbackHandler implements CallbackHandler {

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback current : callbacks) {
                if (current instanceof NameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    ncb.setName("anonymous");
                } else {
                    throw new UnsupportedCallbackException(current);
                }
            }
        }

    }


}
