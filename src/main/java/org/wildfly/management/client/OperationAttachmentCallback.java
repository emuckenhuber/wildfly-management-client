package org.wildfly.management.client;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Emanuel Muckenhuber
 */
public interface OperationAttachmentCallback {

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

    OperationAttachmentCallback NO_ATTACHMENTS = new OperationAttachmentCallback() {
        @Override
        public InputStream getInputStream(final int i) throws IOException {
            throw new IOException("no attachments available");
        }

        @Override
        public int getInputStreamSize(int i) {
            return -1;
        }
    };

}
