package org.wildfly.management.client;

import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
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
     */
    public abstract ManagementClient createClient() throws IOException;

}
