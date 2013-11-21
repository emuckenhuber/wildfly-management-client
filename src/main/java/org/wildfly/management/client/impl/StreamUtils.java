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

import static org.wildfly.management.client._private.ManagementClientLogger.ROOT_LOGGER;
import static org.wildfly.management.client._private.ManagementClientMessages.MESSAGES;

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class StreamUtils {

    private static final int BUFFER_SIZE = 8192;

    private StreamUtils() {
        //
    }

    public static void copyStream(final InputStream in, final OutputStream out) throws IOException {
        final byte[] bytes = new byte[BUFFER_SIZE];
        int cnt;
        while ((cnt = in.read(bytes)) != -1) {
            out.write(bytes, 0, cnt);
        }
    }

    public static void copyStream(final InputStream in, final DataOutput out) throws IOException {
        final byte[] bytes = new byte[BUFFER_SIZE];
        int cnt;
        while ((cnt = in.read(bytes)) != -1) {
            out.write(bytes, 0, cnt);
        }
    }

    public static void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (Throwable t) {
            ROOT_LOGGER.failedToCloseResource(t, closeable);
        }
    }

    public static void expectHeader(final InputStream input, int expected) throws IOException {
        expectHeader(readByte(input), expected);
    }

    public static void expectHeader(final DataInput input, int expected) throws IOException {
        expectHeader(input.readByte(), expected);
    }

    public static void expectHeader(final byte actual, int expected) throws IOException {
        if (actual != (byte) expected) {
            throw MESSAGES.invalidByteToken(expected, actual);
        }
    }

    public static byte readByte(final InputStream stream) throws IOException {
        int b = stream.read();
        if (b == -1) {
            throw new EOFException();
        }
        return (byte) b;
    }
}
