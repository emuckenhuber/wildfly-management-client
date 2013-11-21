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

package org.wildfly.management.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

import org.jboss.dmr.ModelNode;

/**
 * The management connection.
 *
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
    ModelNode execute(ModelNode operation, OperationStreamAttachments attachments) throws IOException;

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
    Future<ModelNode> executeAsync(ModelNode operation, OperationStreamAttachments attachments) throws IOException;

    /**
     * Register the given NotificationHandler to receive notifications emitted by the resource at the given source address.
     * The {@link NotificationHandler#handleNotification(Notification)} method will only be called on the registered handler if the filter's {@link NotificationFilter#isNotificationEnabled(org.jboss.as.controller.client.Notification)}
     * returns @{code true} for the given notification.
     * <br />
     * The source address can be a pattern if at least one of its element value is a wildcard (*).
     *
     * @param address the address of the resource(s) that emit notifications.
     * @param handler the notification handler
     * @param filter  the notification filter. Use {@link NotificationFilter#ALL} to let the handler always handle notifications
     * @return a {@code Closeable} which can be used to unregister the notification handler.
     */
    Closeable registerNotificationHandler(ModelNode address, NotificationHandler handler, NotificationFilter filter);

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
