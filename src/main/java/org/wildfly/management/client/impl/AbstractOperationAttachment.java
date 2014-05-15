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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.wildfly.management.client.OperationStreamAttachments;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class AbstractOperationAttachment implements OperationStreamAttachments.OperationStreamAttachment {

    protected void copyStream(final InputStream is, final OutputStream os) throws IOException {
        StreamUtils.copyStream(is, os);
    }

    public static class ByteArrayStreamAttachment extends AbstractOperationAttachment {

        private final byte[] data;
        public ByteArrayStreamAttachment(byte[] data) {
            this.data = data;
        }

        @Override
        public long size() {
            return data.length;
        }

        @Override
        public void writeTo(OutputStream os) throws IOException {
            os.write(data);
        }
    }

}
