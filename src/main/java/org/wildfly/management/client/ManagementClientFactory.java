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

import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;

import org.xnio.OptionMap;

/**
 * The management client factory.
 * <p/>
 * NOTE: clients are relatively expensive to create unlike opening management connections. In addition the caller is
 * responsible of closing the client to clean up associated resources.
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
        return getInstance(null);
    }

    /**
     * Get an instance of the management client factory.
     *
     * @param type the preferred protocol type
     * @return the management client factory, {@code null} if no matching implementation was found
     */
    public static ManagementClientFactory getInstance(final String type) {
        final ServiceLoader<ManagementClientFactory> loader = ServiceLoader.load(ManagementClientFactory.class);
        final Iterator<ManagementClientFactory> i = loader.iterator();
        while (i.hasNext()) {
            final ManagementClientFactory factory = i.next();
            if (type == null || type.equals(factory.getType())) {
                return factory;
            }
        }
        return null;
    }

    /**
     * Get the factory type.
     *
     * @return the type
     */
    protected abstract String getType();

    /**
     * Create an instance of a management client using the default options.
     *
     * @return the management client
     * @throws IOException
     */
    public abstract ManagementClient createClient() throws IOException;

    /**
     * Create an instance of a management client with a given set of options.
     *
     * @param options the options
     * @return the management client
     * @throws IOException
     */
    public abstract ManagementClient createClient(OptionMap options) throws IOException;

    /**
     * Create an instance of a management client with a given executor.
     *
     * @param executorService the executor service
     * @param options the options
     * @return the management client
     * @throws IOException
     */
    public abstract ManagementClient createClient(ExecutorService executorService, OptionMap options) throws IOException;

}
