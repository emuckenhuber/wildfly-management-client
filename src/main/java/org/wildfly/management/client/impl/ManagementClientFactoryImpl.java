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

import static java.security.AccessController.doPrivileged;

import java.io.IOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.HttpUpgradeConnectionProviderFactory;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.threads.JBossThreadFactory;
import org.wildfly.management.client.ManagementClient;
import org.wildfly.management.client.ManagementClientFactory;
import org.wildfly.management.client.ManagementClientLogger;
import org.wildfly.management.client.ManagementConnection;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;

/**
 * @author Emanuel Muckenhuber
 */
public final class ManagementClientFactoryImpl extends ManagementClientFactory {

    private static final OptionMap DEFAULT_OPTIONS = ManagementClientDefaults.DEFAULT_OPTIONS;
    private static final Xnio xnio = Xnio.getInstance();

    // Global count of created pools
    private static final AtomicInteger executorCount = new AtomicInteger();

    static ExecutorService createDefaultExecutor() {
        final ThreadGroup group = new ThreadGroup("management-client-thread");
        final ThreadFactory threadFactory = new JBossThreadFactory(group, Boolean.FALSE, null, "%G " + executorCount.incrementAndGet() + "-%t", null, null, doPrivileged(new PrivilegedAction<AccessControlContext>() {
            public AccessControlContext run() {
                return AccessController.getContext();
            }
        }));
        return new ThreadPoolExecutor(2, ManagementClientDefaults.DEFAULT_MAX_THREADS, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), threadFactory);
    }

    @Override
    public ManagementClient createClient() throws IOException {
        return createClient(OptionMap.EMPTY);
    }

    @Override
    public ManagementClient createClient(final OptionMap options) throws IOException {
        final OptionMap actual = OptionMap.builder().addAll(DEFAULT_OPTIONS).addAll(options).getMap();

        final Endpoint endpoint = Remoting.createEndpoint(ManagementClientDefaults.DEFAULT_ENDPOINT_NAME, xnio, actual);
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.EMPTY);
        endpoint.addConnectionProvider("http-remoting", new HttpUpgradeConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        endpoint.addConnectionProvider("https-remoting", new HttpUpgradeConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE));
        final ExecutorService executorService = createDefaultExecutor();

        // Create the client and make sure we cleanup resources on close
        final ManagementClientImpl client = createClient(endpoint, options, executorService);
        client.addCloseHandler(new CloseHandler<ManagementClientImpl>() {
            @Override
            public void handleClose(ManagementClientImpl closed, IOException exception) {
                try {
                    endpoint.close();
                } catch (IOException e) {
                    ManagementClientLogger.ROOT_LOGGER.debugf(e, "failed to shutdown endpoint");
                } finally {
                    executorService.shutdownNow();
                }
            }
        });
        return client;
    }

    /**
     * Create a client reusing an existing remoting endpoint and executor.
     *
     * @param endpoint        the remoting endpoint
     * @param options         the options
     * @param executorService the executor service
     * @return the management client
     */
    public static ManagementClientImpl createClient(final Endpoint endpoint, final OptionMap options, final ExecutorService executorService) {
        final OptionMap actual = OptionMap.builder().addAll(DEFAULT_OPTIONS).addAll(options).getMap();
        return internalCreateClient(endpoint, actual, executorService);
    }

    /**
     * Reuse an open remoting connection. This will open a new management channel, however not closing the underlying
     * connection when the management connection is closed.
     *
     * @param connection the connection to reuse
     * @param executor   the executor service to process management requests
     * @param options    the options
     * @return the management connection future
     * @throws IOException
     */
    public static IoFuture<ManagementConnection> reuseOpenConnection(final Connection connection, final ExecutorService executor, final OptionMap options) throws IOException {
        final FutureResult<ManagementConnection> connectionFuture = new FutureResult<>();
        final IoFuture<Channel> future = connection.openChannel(ManagementClientDefaults.CHANNEL_TYPE, options);
        future.addNotifier(new IoFuture.HandlingNotifier<Channel, Void>() {
            @Override
            public void handleCancelled(Void attachment) {
                connectionFuture.setCancelled();
            }

            @Override
            public void handleFailed(IOException exception, Void attachment) {
                connectionFuture.setException(exception);
            }

            @Override
            public void handleDone(Channel data, Void attachment) {
                final ManagementConnectionImpl connection = new ManagementConnectionImpl(data, executor);
                connectionFuture.setResult(connection);
            }
        }, null);
        return connectionFuture.getIoFuture();
    }

    protected static ManagementClientImpl internalCreateClient(final Endpoint endpoint, final OptionMap options, final ExecutorService executorService) {
        return new ManagementClientImpl(endpoint, options, executorService);
    }

}
