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

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

import org.xnio.OptionMap;

/**
 * The management client.
 *
 * @author Emanuel Muckenhuber
 */
public interface ManagementClient extends Closeable {

    /**
     * Open a management connection.
     *
     * @param host the host
     * @param port the port
     * @return the connection future
     * @throws IOException
     */
    Future<ManagementConnection> openConnection(String host, int port) throws IOException;

    /**
     * Open a management connection.
     *
     * @param host    the host
     * @param port    the port
     * @param options the connection options
     * @return the connection future
     * @throws IOException
     */
    Future<ManagementConnection> openConnection(String host, int port, OptionMap options) throws IOException;

    /**
     * Open a management connection.
     *
     * @param host            the host
     * @param port            the port
     * @param callbackHandler the callback handler
     * @param options         the options
     * @return the connection future
     * @throws IOException
     */
    Future<ManagementConnection> openConnection(String host, int port, CallbackHandler callbackHandler, OptionMap options) throws IOException;

    /**
     * Open a management connection.
     *
     * @param host       the host
     * @param port       the port
     * @param sslContext the ssl context
     * @param options    the options
     * @return the connection future
     * @throws IOException
     */
    Future<ManagementConnection> openConnection(String host, int port, SSLContext sslContext, OptionMap options) throws IOException;

    /**
     * Open a management connection.
     *
     * @param host            the host
     * @param port            the port
     * @param callbackHandler the callback handler
     * @param sslContext      the ssl context
     * @param options         the options
     * @return the connection future
     * @throws IOException
     */
    Future<ManagementConnection> openConnection(String host, int port, CallbackHandler callbackHandler, SSLContext sslContext, OptionMap options) throws IOException;

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
