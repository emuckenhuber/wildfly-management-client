package org.wildfly.management.client.impl;

import static org.wildfly.management.client.impl.SecurityActions.getSystemProperty;
import static org.xnio.Options.SASL_POLICY_NOANONYMOUS;
import static org.xnio.Options.SASL_POLICY_NOPLAINTEXT;
import static org.xnio.Options.SASL_PROPERTIES;

import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Property;
import org.xnio.Sequence;

/**
 * @author Emanuel Muckenhuber
 */
class ManagementClientDefaults {

    static final String DEFAULT_ENDPOINT_NAME = "management-client";
    static final String DEFAULT_PROTOCOL = "http-remoting";
    static final int DEFAULT_TIMEOUT = 5000;
    static final int DEFAULT_MAX_THREADS = getSystemProperty("org.wildfly.management.client.client-threads", 2);

    static final OptionMap DEFAULT_OPTIONS;

    static {
        final OptionMap.Builder builder = OptionMap.builder();
        builder.set(SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
        builder.set(SASL_POLICY_NOPLAINTEXT, Boolean.FALSE);
        builder.set(SASL_PROPERTIES, Sequence.of(Property.of("jboss.sasl.local-user.quiet-auth", "true")));
        builder.set(Options.SSL_ENABLED, true);
        builder.set(Options.SSL_STARTTLS, true);
        DEFAULT_OPTIONS = builder.getMap();
    }

}
