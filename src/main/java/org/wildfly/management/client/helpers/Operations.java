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

import static org.wildfly.management.client._private.ManagementClientMessages.MESSAGES;
import static org.wildfly.management.client.helpers.ClientConstants.ADD;
import static org.wildfly.management.client.helpers.ClientConstants.COMPOSITE;
import static org.wildfly.management.client.helpers.ClientConstants.FAILURE_DESCRIPTION;
import static org.wildfly.management.client.helpers.ClientConstants.NAME;
import static org.wildfly.management.client.helpers.ClientConstants.OP;
import static org.wildfly.management.client.helpers.ClientConstants.OP_ADDR;
import static org.wildfly.management.client.helpers.ClientConstants.OUTCOME;
import static org.wildfly.management.client.helpers.ClientConstants.READ_ATTRIBUTE_OPERATION;
import static org.wildfly.management.client.helpers.ClientConstants.READ_RESOURCE_OPERATION;
import static org.wildfly.management.client.helpers.ClientConstants.RECURSIVE;
import static org.wildfly.management.client.helpers.ClientConstants.REMOVE_OPERATION;
import static org.wildfly.management.client.helpers.ClientConstants.RESULT;
import static org.wildfly.management.client.helpers.ClientConstants.ROLLBACK_ON_RUNTIME_FAILURE;
import static org.wildfly.management.client.helpers.ClientConstants.STEPS;
import static org.wildfly.management.client.helpers.ClientConstants.SUCCESS;
import static org.wildfly.management.client.helpers.ClientConstants.UNDEFINE_ATTRIBUTE_OPERATION;
import static org.wildfly.management.client.helpers.ClientConstants.VALUE;
import static org.wildfly.management.client.helpers.ClientConstants.WRITE_ATTRIBUTE_OPERATION;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.management.client.ManagementConnection;

/**
 * A helper class for various operation tasks. Includes helpers to create standard operations, check whether the
 * operation was executed successfully, get the failure description if unsuccessful, etc.
 * <p/>
 * <b>Example:</b> Read the server state
 * <pre>
 *     <code>
 *        final ManagementClient client = ManagementClientFactory.getInstance().createClient();
 *        try {
 *           final Future<ManagementConnection> futureConnection = client.openConnection("localhost", 9990);
 *           final ManagementConnection connection = futureConnection.get();
 *           try {
 *               final ModelNode address = new ModelNode().setEmptyList();
 *               // Read the server state
 *               final ModelNode operation = Operations.createReadAttributeOperation(address, "server-state");
 *               final ModelNode result = connection.execute(operation);
 *               if (Operations.isSuccessfulOutcome(result)) {
 *                   System.out.printf("Server state: %s%n", Operations.readResult(result));
 *               } else {
 *                   System.out.printf("Failure! %s%n", Operations.getFailureDescription(result));
 *               }
 *           } finally {
 *               if (connection != null) try {
 *                   connection.close();
 *               } catch (IOException ignored) {
 *                   //
 *               }
 *           }
 *       } finally {
 *           if (client != null) try {
 *               client.close();
 *           } catch (IOException ignored) {
 *               //
 *           }
 *       }
 *     </code>
 * </pre>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Emanuel Muckenhuber
 */
public class Operations {

    private static final String RELOAD = "reload";
    private static final String SHUTDOWN = "shutdown";
    private static final ModelNode EMPTY = new ModelNode();

    static {
        EMPTY.protect();
    }

    /**
     * Checks the result for a successful operation outcome.
     *
     * @param outcome the result of executing an operation
     * @return {@code true} if the operation was successful, otherwise {@code false}
     */
    public static boolean isSuccessfulOutcome(final ModelNode outcome) {
        return outcome.get(OUTCOME).asString().equals(SUCCESS);
    }

    /**
     * Parses the result and returns the failure description.
     *
     * @param result the result of executing an operation
     * @return the failure description if defined, otherwise a new undefined model node
     * @throws IllegalArgumentException if the outcome of the operation was successful
     */
    public static ModelNode getFailureDescription(final ModelNode result) {
        if (isSuccessfulOutcome(result)) {
            throw MESSAGES.noFailureDescription();
        }
        if (result.hasDefined(FAILURE_DESCRIPTION)) {
            return result.get(FAILURE_DESCRIPTION);
        }
        return new ModelNode();
    }

    /**
     * Returns the address for the operation.
     *
     * @param op the operation
     * @return the operation address or a new undefined model node
     */
    public static ModelNode getOperationAddress(final ModelNode op) {
        return op.hasDefined(OP_ADDR) ? op.get(OP_ADDR) : new ModelNode();
    }

    /**
     * Returns the name of the operation.
     *
     * @param op the operation
     * @return the name of the operation
     * @throws IllegalArgumentException if the operation was not defined.
     */
    public static String getOperationName(final ModelNode op) {
        if (op.hasDefined(OP)) {
            return op.get(OP).asString();
        }
        throw MESSAGES.operationNameNotFound();
    }

    /**
     * Creates an add operation.
     *
     * @param address the address for the operation
     * @return the operation
     */
    public static ModelNode createAddOperation(final ModelNode address) {
        return createOperation(ADD, address);
    }

    /**
     * Creates a remove operation.
     *
     * @param address the address for the operation
     * @return the operation
     */
    public static ModelNode createRemoveOperation(final ModelNode address) {
        return createOperation(REMOVE_OPERATION, address);
    }

    /**
     * Creates a composite operation with an empty address and empty steps that will rollback on a runtime failure.
     * <p/>
     * By default the {@link ClientConstants#ROLLBACK_ON_RUNTIME_FAILURE} is set to {@code true} to rollback all
     * operations if one fails.
     *
     * @return the operation
     */
    public static ModelNode createCompositeOperation() {
        final ModelNode op = createOperation(COMPOSITE);
        op.get(ROLLBACK_ON_RUNTIME_FAILURE).set(true);
        op.get(STEPS).setEmptyList();
        return op;
    }

    /**
     * Creates an operation to read the attribute represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the read attribute for
     * @param attributeName the name of the parameter to read
     * @return the operation
     */
    public static ModelNode createReadAttributeOperation(final ModelNode address, final String attributeName) {
        final ModelNode op = createOperation(READ_ATTRIBUTE_OPERATION, address);
        op.get(NAME).set(attributeName);
        return op;
    }

    /**
     * Creates a non-recursive operation to read a resource.
     *
     * @param address the address to create the read for
     * @return the operation
     */
    public static ModelNode createReadResourceOperation(final ModelNode address) {
        return createReadResourceOperation(address, false);
    }

    /**
     * Creates an operation to read a resource.
     *
     * @param address   the address to create the read for
     * @param recursive whether to search recursively or not
     * @return the operation
     */
    public static ModelNode createReadResourceOperation(final ModelNode address, final boolean recursive) {
        final ModelNode op = createOperation(READ_RESOURCE_OPERATION, address);
        op.get(RECURSIVE).set(recursive);
        return op;
    }

    /**
     * Creates an operation to undefine an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name attribute to undefine
     * @return the operation
     */
    public static ModelNode createUndefineAttributeOperation(final ModelNode address, final String attributeName) {
        final ModelNode op = createOperation(UNDEFINE_ATTRIBUTE_OPERATION, address);
        op.get(NAME).set(attributeName);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final boolean value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final int value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final long value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final String value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates an operation to write an attribute value represented by the {@code attributeName} parameter.
     *
     * @param address       the address to create the write attribute for
     * @param attributeName the name of the attribute to write
     * @param value         the value to set the attribute to
     * @return the operation
     */
    public static ModelNode createWriteAttributeOperation(final ModelNode address, final String attributeName, final ModelNode value) {
        final ModelNode op = createNoValueWriteOperation(address, attributeName);
        op.get(VALUE).set(value);
        return op;
    }

    /**
     * Creates a generic operation with an empty (root) address.
     *
     * @param operation the operation to create
     * @return the operation
     */
    public static ModelNode createOperation(final String operation) {
        final ModelNode op = new ModelNode();
        op.get(OP).set(operation);
        op.get(OP_ADDR).setEmptyList();
        return op;
    }

    /**
     * Creates an operation.
     *
     * @param operation the operation name
     * @param address   the address for the operation
     * @return the operation
     * @throws IllegalArgumentException if the address is not of type {@link org.jboss.dmr.ModelType#LIST}
     */
    public static ModelNode createOperation(final String operation, final ModelNode address) {
        if (address.getType() != ModelType.LIST) {
            throw MESSAGES.invalidAddressType();
        }
        final ModelNode op = createOperation(operation);
        op.get(OP_ADDR).set(address);
        return op;
    }

    /**
     * Reads the result of an operation and returns the result. If the operation does not have a {@link
     * ClientConstants#RESULT} attribute, a new undefined {@link org.jboss.dmr.ModelNode} is returned.
     *
     * @param result the result of executing an operation
     * @return the result of the operation or a new undefined model node
     */
    public static ModelNode readResult(final ModelNode result) {
        return (result.hasDefined(RESULT) ? result.get(RESULT) : new ModelNode());
    }

    private static ModelNode createNoValueWriteOperation(final ModelNode address, final String attributeName) {
        final ModelNode op = createOperation(WRITE_ATTRIBUTE_OPERATION, address);
        op.get(NAME).set(attributeName);
        return op;
    }

    /**
     * Utility method to execute a reload operation on a given target. This will wait for the connection to be closed
     * before returning the result
     *
     * @param connection the connection
     * @param address    the target address
     * @return {@code true} if the operation executed successfully, {@code false} otherwise
     * @throws java.io.IOException
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
