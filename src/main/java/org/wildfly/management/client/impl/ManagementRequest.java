package org.wildfly.management.client.impl;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.wildfly.management.client.OperationAttachments;

/**
 * A management request.
 *
 * @author Emanuel Muckenhuber
 */
interface ManagementRequest {

    /**
     * Get the operation attachments.
     *
     * @return the operation attachments
     */
    OperationAttachments getAttachments();

    /**
     * Get the request operation id.
     *
     * @return the operation id
     */
    int getOperationId();

    /**
     * Get the operation type.
     *
     * @return the request type
     */
    byte getRequestType();

    /**
     * Write the request.
     *
     * @param output the data output
     * @throws IOException
     */
    void writeRequest(DataOutput output) throws IOException;

    /**
     * Handle the response.
     *
     * @param header the response header
     * @param input  the data input
     * @throws IOException
     */
    void handleResponse(ManagementResponseHeader header, DataInput input) throws IOException;

    /**
     * Handle a protocol failure.
     *
     * @param exception the exception
     */
    void handleFailure(IOException exception);

    /**
     * Async cancel the request
     */
    void asyncCancel();

}
