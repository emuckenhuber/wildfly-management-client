package org.wildfly.management.client.impl;

import static org.xnio.IoFuture.HandlingNotifier;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.jboss.as.controller.client.ControllerClientLogger;
import org.jboss.as.controller.client.ControllerClientMessages;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.wildfly.management.client.ManagementClient;
import org.wildfly.management.client.ManagementClientOptions;
import org.wildfly.management.client.ManagementConnection;
import org.xnio.Cancellable;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;

/**
 * @author Emanuel Muckenhuber
 */
public class ManagementClientImpl extends AbstractHandleableCloseable<ManagementClientImpl> implements ManagementClient {

    private static final String JBOSS_LOCAL_USER = "JBOSS-LOCAL-USER";
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
    public Future<ManagementConnection> openConnection(String host, int port, OptionMap connectionOptions) throws IOException {
        return openConnection(host, port, null, null, connectionOptions);
    }

    @Override
    public Future<ManagementConnection> openConnection(String host, int port, CallbackHandler callbackHandler, OptionMap options) throws IOException {
        return openConnection(host, port, callbackHandler, null, options);
    }

    @Override
    public Future<ManagementConnection> openConnection(String host, int port, SSLContext sslContext, OptionMap options) throws IOException {
        return openConnection(host, port, null, sslContext, options);
    }

    @Override
    public Future<ManagementConnection> openConnection(String host, int port, CallbackHandler callbackHandler, SSLContext sslContext, OptionMap options) throws IOException {
        final InetSocketAddress address = new InetSocketAddress(host, port);
        return internalConnect(null, address, options, callbackHandler, sslContext);
    }

    Future<ManagementConnection> internalConnect(final SocketAddress bindAddress, final InetSocketAddress destination,
                                     final OptionMap connectOptions, final CallbackHandler callbackHandler,
                                     final SSLContext sslContext) throws IOException {

        final CallbackHandler actualHandler = callbackHandler != null ? callbackHandler : new AnonymousCallbackHandler();
        final ManagementConnectionFuture.WrapperCallbackHandler wrapperHandler = new ManagementConnectionFuture.WrapperCallbackHandler(actualHandler);
        final String protocol = connectOptions.get(ManagementClientOptions.PROTOCOL, ManagementClientDefaults.DEFAULT_PROTOCOL);
        final OptionMap.Builder builder = OptionMap.builder().addAll(options).addAll(connectOptions);
        configureSaslMechnisms(null, isLocal(destination.getHostName()), builder);
        final OptionMap options = builder.getMap();
        final IoFuture<ManagementConnection> future = internalOpenConnection(protocol, bindAddress, destination, options, wrapperHandler, sslContext);
        long timeoutMillis = connectOptions.get(ManagementClientOptions.CONNECTION_TIMEOUT, ManagementClientDefaults.DEFAULT_TIMEOUT);
        final ManagementConnectionFuture result = new ManagementConnectionFuture(wrapperHandler, future, timeoutMillis);
        return result;
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
                        public void handleDone(Channel channel, Void attachment) {
                            final ManagementConnectionImpl connection = new ManagementConnectionImpl(channel, getExecutor());
                            connections.add(connection);
                            connection.addCloseHandler(connectionCloseHandler);
                            result.setResult(connection);
                        }
                    }, null);
                }
            }, null);
            result.addCancelHandler(new Cancellable() {
                @Override
                public Cancellable cancel() {
                    connectionFuture.cancel();
                    return this;
                }
            });
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

    private static void configureSaslMechnisms(Map<String, String> saslOptions, boolean isLocal, OptionMap.Builder builder) {
        String[] mechanisms = null;
        String listed;
        if (saslOptions != null && (listed = saslOptions.get(Options.SASL_DISALLOWED_MECHANISMS.getName())) != null) {
            // Disallowed mechanisms were passed via the saslOptions map; need to convert to an XNIO option
            String[] split = listed.split(" ");
            if (isLocal) {
                mechanisms = new String[split.length + 1];
                mechanisms[0] = JBOSS_LOCAL_USER;
                System.arraycopy(split, 0, mechanisms, 1, split.length);
            } else {
                mechanisms = split;
            }
        } else if (!isLocal) {
            mechanisms = new String[]{ JBOSS_LOCAL_USER };
        }

        if (mechanisms != null) {
            builder.set(Options.SASL_DISALLOWED_MECHANISMS, Sequence.of(mechanisms));
        }

        if (saslOptions != null && (listed = saslOptions.get(Options.SASL_MECHANISMS.getName())) != null) {
            // SASL mechanisms were passed via the saslOptions map; need to convert to an XNIO option
            String[] split = listed.split(" ");
            if (split.length > 0) {
                builder.set(Options.SASL_MECHANISMS, Sequence.of(split));
            }
        }
    }

    private static boolean isLocal(final String hostName) {
        try {
            final InetAddress address = InetAddress.getByName(hostName);
            NetworkInterface nic;
            if (address.isLinkLocalAddress()) {
                /*
                 * AS7-6382 On Windows the getByInetAddress was not identifying a NIC where the address did not have the zone
                 * ID, this manual iteration does allow for the address to be matched.
                 */
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                nic = null;
                while (interfaces.hasMoreElements() && nic == null) {
                    NetworkInterface current = interfaces.nextElement();
                    Enumeration<InetAddress> addresses = current.getInetAddresses();
                    while (addresses.hasMoreElements() && nic == null) {
                        InetAddress currentAddress = addresses.nextElement();
                        if (address.equals(currentAddress)) {
                            nic = current;
                        }
                    }
                }
            } else {
                nic = NetworkInterface.getByInetAddress(address);
            }
            return address.isLoopbackAddress() || nic != null;
        } catch (Exception e) {
            return false;
        }
    }


}
