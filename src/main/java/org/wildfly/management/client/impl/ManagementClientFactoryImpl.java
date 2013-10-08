package org.wildfly.management.client.impl;

import static java.security.AccessController.doPrivileged;

import java.io.IOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.controller.client.ControllerClientLogger;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.HttpUpgradeConnectionProviderFactory;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.threads.JBossThreadFactory;
import org.wildfly.management.client.ManagementClient;
import org.wildfly.management.client.ManagementClientFactory;
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
                    ControllerClientLogger.ROOT_LOGGER.debugf(e, "failed to shutdown endpoint");
                } finally {
                    executorService.shutdownNow();
                }
            }
        });
        return client;
    }

    public static ManagementClientImpl createClient(final Endpoint endpoint, final OptionMap options, final ExecutorService executorService) {
        final OptionMap actual = OptionMap.builder().addAll(DEFAULT_OPTIONS).addAll(options).getMap();
        return internalCreateClient(endpoint, actual, executorService);
    }

    protected static ManagementClientImpl internalCreateClient(final Endpoint endpoint, final OptionMap options, final ExecutorService executorService) {
        return new ManagementClientImpl(endpoint, options, executorService);
    }

}
