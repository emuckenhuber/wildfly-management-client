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

package org.wildfly.management.client.helpers;

import static org.wildfly.management.client.helpers.ClientConstants.OP;
import static org.wildfly.management.client.helpers.ClientConstants.OP_ADDR;
import static org.wildfly.management.client.helpers.ClientConstants.OUTCOME;
import static org.wildfly.management.client.helpers.ClientConstants.SUCCESS;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.jboss.dmr.ModelNode;
import org.wildfly.management.client.ManagementConnection;

/**
 * @author Emanuel Muckenhuber
 */
public class ClientUtils {

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
