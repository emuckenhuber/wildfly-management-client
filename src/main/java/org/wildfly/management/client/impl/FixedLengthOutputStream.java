/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import java.io.EOFException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Emanuel Muckenhuber
 */
class FixedLengthOutputStream extends FilterOutputStream {

    private long remaining;
    private boolean closed;

    public FixedLengthOutputStream(OutputStream out, long size) {
        super(out);
        this.remaining = size;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("stream closed");
        }
        if (len > remaining) {
            throw new EOFException();
        }
        super.write(b, off, len);
        remaining -= len;
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("stream closed");
        }
        if (remaining == 0) {
            throw new EOFException();
        }
        super.write(b);
        remaining--;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        super.close();
    }
}
