package org.wildfly.management.client.impl;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.client.ControllerClientLogger;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Endpoint;
import org.wildfly.management.client.ManagementClient;
import org.wildfly.management.client.ManagementClientFactory;

/**
 * @author Emanuel Muckenhuber
 */
public class ManagementClientFactoryImpl extends ManagementClientFactory {

    @Override
    public ManagementClient createClient() throws IOException {

        final Endpoint endpoint = null;
        final ExecutorService executorService = null;
        // Create the client and make sure we cleanup resources on close
        final ManagementClientImpl client = new ManagementClientImpl(endpoint, executorService);
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

    public ManagementClient createClient(final Endpoint endpoint, final ExecutorService executorService) {
        return new ManagementClientImpl(endpoint, executorService);
    }

}
