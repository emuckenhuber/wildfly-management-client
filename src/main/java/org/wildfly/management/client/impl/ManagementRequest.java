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
