package org.wildfly.management.client;

import org.xnio.Option;

/**
 * @author Emanuel Muckenhuber
 */
public final class ManagementClientOptions {

    private ManagementClientOptions() {
        //
    }

    public static final Option<String> PROTOCOL = Option.simple(ManagementClientOptions.class, "PROTOCOL", String.class);
    public static final Option<Integer> CONNECTION_TIMEOUT = Option.simple(ManagementClientOptions.class, "CONNECTION_TIMEOUT", Integer.class);

}
