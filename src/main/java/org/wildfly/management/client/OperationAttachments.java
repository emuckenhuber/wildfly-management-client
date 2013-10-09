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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * The operation attachments.
 *
 * @author Emanuel Muckenhuber
 */
public interface OperationAttachments {

    /**
     * Get the number of attached streams.
     *
     * @return the number of attached streams
     */
    int getNumberOfAttachedStreams();

    /**
     * Get the input stream size.
     *
     * @param i the input stream index
     * @return the stream size
     */
    int getInputStreamSize(int i);

    /**
     * Get the input stream for a give input-stream index.
     *
     * @param i the input stream index
     * @return the input stream
     * @throws IOException
     */
    InputStream getInputStream(int i) throws IOException;

    OperationAttachments NO_ATTACHMENTS = new OperationAttachments() {

        @Override
        public int getNumberOfAttachedStreams() {
            return 0;
        }

        @Override
        public InputStream getInputStream(final int i) throws IOException {
            throw new IOException("no attachments available");
        }

        @Override
        public int getInputStreamSize(int i) {
            return -1;
        }
    };

    public class FileOperationAttachments implements OperationAttachments {

        final File[] files;

        protected FileOperationAttachments(File[] files) {
            this.files = files;
        }

        public static FileOperationAttachments create(final File... files) throws IOException {
            for (final File file : files) {
                if (!file.isFile() || !file.canRead()) {
                    throw new FileNotFoundException(file.getAbsolutePath());
                }
            }
            return new FileOperationAttachments(files);
        }

        @Override
        public int getNumberOfAttachedStreams() {
            return files.length;
        }

        protected File getFile(int i) {
            return files[i];
        }

        @Override
        public int getInputStreamSize(int i) {
            return (int) getFile(i).length();
        }

        @Override
        public InputStream getInputStream(int i) throws IOException {
            return new FileInputStream(getFile(i));
        }
    }

}
