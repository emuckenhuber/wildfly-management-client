package org.wildfly.management.client.impl;

import java.io.IOException;

import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.wildfly.management.client.ManagementClient;
import org.wildfly.management.client.ManagementClientFactory;
import org.wildfly.management.client.ManagementConnection;
import org.wildfly.management.client.impl.StreamUtils;
import org.wildfly.management.client.helpers.ClientConstants;
import org.wildfly.management.client.helpers.Operations;
import org.xnio.OptionMap;

/**
 * @author Emanuel Muckenhuber
 */
@Ignore("needs WildFly running")
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

    @Test
    public void otherTest() throws Exception {

        final ManagementConnection connection = openConnection();
        try {

            final ModelNode address = new ModelNode().setEmptyList();
            // Read the server state
            final ModelNode operation = Operations.createReadAttributeOperation(address, "server-state");
            final ModelNode result = connection.execute(operation);
            if (Operations.isSuccessfulOutcome(result)) {
                System.out.printf("Server state: %s%n", Operations.readResult(result));
            } else {
                System.out.printf("Failure! %s%n", Operations.getFailureDescription(result));
            }

        } finally {
            StreamUtils.safeClose(connection);
        }
    }

}
