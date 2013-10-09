package org.wildfly.management.client.tests;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.HttpUpgradeConnectionProviderFactory;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.wildfly.management.client.ManagementClient;
import org.wildfly.management.client.ManagementClientOptions;
import org.wildfly.management.client.ManagementConnection;
import org.wildfly.management.client.impl.StreamUtils;
import org.wildfly.management.client.impl.ManagementClientFactoryImpl;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * @author Emanuel Muckenhuber
 */
public class AbstractMgmtClientTestCase {

    protected TestServer server;

    private static Endpoint endpoint;
    private static ExecutorService executorService;
    private static Xnio xnio = Xnio.getInstance();
    private static ManagementClient client;
    private static AcceptingChannel<? extends ConnectedStreamChannel> streamServer;

    static final int PORT = 9990;

    @BeforeClass
    public static void beforeClass() throws IOException {
        boolean ok = false;
        try {
            endpoint = Remoting.createEndpoint("test-endpoint", xnio, OptionMap.EMPTY);
            endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
            endpoint.addConnectionProvider("http-remoting", new HttpUpgradeConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
            endpoint.addConnectionProvider("https-remoting", new HttpUpgradeConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE));

            // Create network server provider
            final NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
            final SimpleServerAuthenticationProvider provider = new SimpleServerAuthenticationProvider();
            final OptionMap options = OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("ANONYMOUS"), Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
            streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", PORT), options, provider, null);

            executorService = Executors.newCachedThreadPool();

            client = ManagementClientFactoryImpl.createClient(endpoint, OptionMap.EMPTY, executorService);

            ok = true;
        } finally {
            if (!ok) {
                StreamUtils.safeClose(endpoint);
            }
        }
    }

    @AfterClass
    public static void afterClass() {
        StreamUtils.safeClose(client);
        StreamUtils.safeClose(streamServer);
        StreamUtils.safeClose(endpoint);
        executorService.shutdownNow();
    }

    @Before
    public void setUp() throws IOException {
        server = new TestServer(executorService);
        server.register(endpoint);
    }

    @After
    public void tearDown() {
        StreamUtils.safeClose(server);
        server = null;
    }

    protected ManagementConnection openConnection() throws IOException {
        try {
            return client.openConnection("localhost", PORT, OptionMap.create(ManagementClientOptions.PROTOCOL, "remote")).get();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

}
