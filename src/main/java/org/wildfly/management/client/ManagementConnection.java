package org.wildfly.management.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
public interface ManagementConnection extends Closeable {

    /**
     * Execute an operation synchronously.
     *
     * @param operation the operation to execute
     * @return the result of the operation
     * @throws java.io.IOException if an I/O error occurs while executing the operation
     */
    ModelNode execute(ModelNode operation) throws IOException;

    /**
     * Execute an operation synchronously.
     *
     * @param operation   the operation to execute
     * @param attachments the operation attachments
     * @return the result of the operation
     * @throws java.io.IOException if an I/O error occurs while executing the operation
     */
    ModelNode execute(ModelNode operation, OperationAttachmentCallback attachments) throws IOException;

    /**
     * Execute an operation asynchronously.
     *
     * @param operation the operation to execute
     * @return the future result of the operation
     */
    Future<ModelNode> executeAsync(ModelNode operation) throws IOException;

    /**
     * Execute an operation asynchronously.
     *
     * @param operation   the operation to execute
     * @param attachments the operation attachments
     * @return the future result of the operation
     */
    Future<ModelNode> executeAsync(ModelNode operation, OperationAttachmentCallback attachments) throws IOException;

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
