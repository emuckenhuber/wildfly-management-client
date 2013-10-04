package org.wildfly.management.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

/**
 * @author Emanuel Muckenhuber
 */
public interface ManagementClient extends Closeable {

    /**
     * Open a management connection.
     *
     * @return the management connection
     */
    Future<ManagementConnection> openConnection() throws IOException;

    /**
     * Wait for a resource close to complete.
     *
     * @throws InterruptedException if the operation is interrupted
     */
    void awaitClosed() throws InterruptedException;

    /**
     * Wait for a resource close to complete.
     */
    void awaitClosedUninterruptibly();

}
