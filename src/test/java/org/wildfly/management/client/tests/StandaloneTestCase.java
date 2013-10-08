package org.wildfly.management.client.tests;

import java.io.IOException;

import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.management.client.ManagementClient;
import org.wildfly.management.client.ManagementClientFactory;
import org.wildfly.management.client.ManagementConnection;
import org.xnio.OptionMap;

/**
 * @author Emanuel Muckenhuber
 */
public class StandaloneTestCase {

    private static ManagementClient client;

    @BeforeClass
    public static void beforeClass() throws IOException {
        client = ManagementClientFactory.getInstance().createClient();
    }

    @AfterClass
    public static void afterClass() {
        StreamUtils.safeClose(client);
    }

    protected ManagementConnection openConnection() throws Exception {
        return client.openConnection("localhost", 9990, OptionMap.EMPTY).get();
    }

    @Test
    public void test() throws Exception {

        final ManagementConnection connection = openConnection();
        try {

            final ModelNode operation = new ModelNode();
            operation.get(ClientConstants.OP_ADDR).setEmptyList();
            operation.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
            operation.get(ClientConstants.RECURSIVE).set(true);

            final ModelNode result = connection.execute(operation);
            System.out.println(result);

        } finally {
            StreamUtils.safeClose(connection);
        }
    }

}
