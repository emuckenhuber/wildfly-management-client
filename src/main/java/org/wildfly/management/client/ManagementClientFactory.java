package org.wildfly.management.client;

import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceLoader;

import org.xnio.OptionMap;

/**
 * The management client factory.
 *
 * NOTE: clients are relatively expensive to create despite opening connections. Also the caller is responsible of closing
 * the client to cleanup all associated resources.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class ManagementClientFactory {

    /**
     * Get an instance of the management client factory.
     *
     * @return the management client factory
     */
    public static ManagementClientFactory getInstance() {
        final ServiceLoader<ManagementClientFactory> loader = ServiceLoader.load(ManagementClientFactory.class);
        final Iterator<ManagementClientFactory> i = loader.iterator();
        return i.next();
    }

    /**
     * Create an instance of a management client
     *
     * @return the management client
     * @throws IOException
     */
    public abstract ManagementClient createClient() throws IOException;

    /**
     * Create an instance of a management client.
     *
     * @param options the options
     * @return the management client
     * @throws IOException
     */
    public abstract ManagementClient createClient(OptionMap options) throws IOException;

}
