package org.wildfly.management.client;

import static org.jboss.as.controller.client.helpers.ClientConstants.OP;
import static org.jboss.as.controller.client.helpers.ClientConstants.OP_ADDR;
import static org.jboss.as.controller.client.helpers.ClientConstants.OUTCOME;
import static org.jboss.as.controller.client.helpers.ClientConstants.SUCCESS;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
public class ManagementClientUtil {

    private static final String RELOAD = "reload";
    private static final String SHUTDOWN = "shutdown";
    private static final ModelNode EMPTY = new ModelNode();

    static {
        EMPTY.protect();
    }

    /**
     * Utility method to execute a reload operation on a given target. This will wait for the connection to be closed
     * before returning the result
     *
     * @param connection the connection
     * @param address    the target address
     * @return {@code true} if the operation executed successfully, {@code false} otherwise
     * @throws IOException
     */
    public static boolean executeReload(final ManagementConnection connection, final ModelNode address) throws IOException {
        // Create the reload operation
        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(address);
        operation.get(OP).set(RELOAD);
        // Execute and await the connection closed
        return executeAwaitClosed(connection, operation);
    }

    /**
     * Utility method to execute a shutdown operation on a given target. This will wait for the connection to be closed
     * before returning the result.
     *
     * @param connection the connection
     * @param address    the target address
     * @return {@code true} if the operation executed successfully, {@code false} otherwise
     * @throws IOException
     */
    public static boolean executeShutdown(final ManagementConnection connection, final ModelNode address) throws IOException {
        // Create the shutdown operation
        final ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(address);
        operation.get(OP).set(SHUTDOWN);
        // Execute and await the connection closed
        return executeAwaitClosed(connection, operation);
    }

    private static boolean executeAwaitClosed(final ManagementConnection connection, final ModelNode operation) throws IOException {
        final ModelNode result = connection.execute(operation);
        if (!SUCCESS.equals(result.get(OUTCOME).asString())) {
            return false;
        }
        try {
            connection.awaitClosed();
        } catch (InterruptedException e) {
            throw new InterruptedIOException();
        }
        return true;
    }

}
