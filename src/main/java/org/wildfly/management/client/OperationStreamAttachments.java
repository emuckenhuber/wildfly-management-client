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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.wildfly.management.client.impl.AbstractOperationAttachment;
import org.xnio.IoUtils;

/**
 * The operation stream attachments.
 *
 * @author Emanuel Muckenhuber
 */
public interface OperationStreamAttachments {

    /**
     * Get the number of attached streams.
     *
     * @return the number of attached streams
     */
    int getNumberOfAttachedStreams();

    /**
     * Get an operation stream attachment.
     *
     * @param i the attachment index
     * @return the stream attachment
     */
    OperationStreamAttachment getAttachment(final int i);

    /**
     * A single stream attachment.
     */
    interface OperationStreamAttachment {

        /**
         * Get the size of the stream.
         *
         * @return the size
         */
        long size();

        /**
         * Write the attachment to an output stream.
         *
         * @param os the output stream
         * @throws java.io.IOException
         */
        void writeTo(OutputStream os) throws IOException;

    }

    OperationStreamAttachments NO_ATTACHMENTS = new OperationStreamAttachments() {

        @Override
        public int getNumberOfAttachedStreams() {
            return 0;
        }

        @Override
        public OperationStreamAttachment getAttachment(int i) {
            throw new IndexOutOfBoundsException();
        }
    };

    public static class Builder {

        private List<OperationStreamAttachment> attachments = new ArrayList<>();

        private Builder() {
            //
        }

        public static Builder create(final OperationStreamAttachment... attachments) {
            final Builder builder = new Builder();
            builder.add(attachments);
            return builder;
        }

        public static Builder create(final File... attachments) {
            final Builder builder = new Builder();
            builder.add(attachments);
            return builder;
        }

        public Builder add(final OperationStreamAttachment... attachments) {
            for (final OperationStreamAttachment attachment : attachments) {
                this.attachments.add(attachment);
            }
            return this;
        }

        public Builder add(final File... attachments) {
            for (final File attachment : attachments) {
                this.attachments.add(new FileStreamAttachment(attachment));
            }
            return this;
        }

        public OperationStreamAttachments build() {
            return new OperationStreamAttachments() {
                @Override
                public int getNumberOfAttachedStreams() {
                    return attachments.size();
                }

                @Override
                public OperationStreamAttachment getAttachment(int i) {
                    return attachments.get(i);
                }
            };
        }

    }

    class FileStreamAttachment extends AbstractOperationAttachment {

        private final File file;
        FileStreamAttachment(File file) {
            this.file = file;
        }

        @Override
        public long size() {
            return file.length();
        }

        @Override
        public void writeTo(OutputStream os) throws IOException {
            final FileInputStream is = new FileInputStream(file);
            try {
                copyStream(is, os);
            } finally {
                IoUtils.safeClose(is);
            }
        }

    }

}
