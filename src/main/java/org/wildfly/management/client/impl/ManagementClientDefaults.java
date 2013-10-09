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

import static org.wildfly.management.client.impl.SecurityActions.getSystemProperty;
import static org.xnio.Options.SASL_POLICY_NOANONYMOUS;
import static org.xnio.Options.SASL_POLICY_NOPLAINTEXT;
import static org.xnio.Options.SASL_PROPERTIES;

import org.wildfly.management.client.ManagementClientOptions;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Property;
import org.xnio.Sequence;

/**
 * @author Emanuel Muckenhuber
 */
class ManagementClientDefaults {

    static final String CHANNEL_TYPE = "management";
    static final String DEFAULT_ENDPOINT_NAME = "management-client";
    static final String DEFAULT_PROTOCOL = "http-remoting";
    static final int DEFAULT_TIMEOUT = 5000;
    static final int DEFAULT_MAX_THREADS = getSystemProperty("org.wildfly.management.client.client-threads", 2);
    static final String CLIENT_BIND_ADDRESS = getSystemProperty("jboss.management.client_socket_bind_address");

    static final OptionMap DEFAULT_OPTIONS;

    static {
        final OptionMap.Builder builder = OptionMap.builder();
        builder.set(SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
        builder.set(SASL_POLICY_NOPLAINTEXT, Boolean.FALSE);
        builder.set(SASL_PROPERTIES, Sequence.of(Property.of("jboss.sasl.local-user.quiet-auth", "true")));
        if (CLIENT_BIND_ADDRESS != null) {
            builder.set(ManagementClientOptions.CLIENT_BIND_ADDRESS, CLIENT_BIND_ADDRESS);
        }
        builder.set(Options.SSL_ENABLED, true);
        builder.set(Options.SSL_STARTTLS, true);
        DEFAULT_OPTIONS = builder.getMap();
    }

}
