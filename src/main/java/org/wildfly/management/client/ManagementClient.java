package org.wildfly.management.client;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;

import org.xnio.OptionMap;

/**
 * @author Emanuel Muckenhuber
 */
public interface ManagementClient extends Closeable {

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
