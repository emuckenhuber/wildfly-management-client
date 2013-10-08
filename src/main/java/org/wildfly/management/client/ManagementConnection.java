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
     * Register the given NotificationHandler to receive notifications emitted by the resource at the given source address.
     * The {@link NotificationHandler#handleNotification(Notification)} method will only be called on the registered handler if the filter's {@link NotificationFilter#isNotificationEnabled(org.jboss.as.controller.client.Notification)}
     * returns @{code true} for the given notification.
     * <br />
     * The source address can be a pattern if at least one of its element value is a wildcard (*).
     *
     * @param address the address of the resource(s) that emit notifications.
     * @param handler the notification handler
     * @param filter the notification filter. Use {@link NotificationFilter#ALL} to let the handler always handle notifications
     */
    NotificationRegistration registerNotificationHandler(ModelNode address, NotificationHandler handler, NotificationFilter filter);

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

    /**
     * Unregister a previously registered NotificationHandler.
     */
    interface NotificationRegistration {
        void unregister();
    }

}
