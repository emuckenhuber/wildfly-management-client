package org.wildfly.management.client.impl;

import static org.wildfly.management.client.impl.StreamUtils.expectHeader;
import static org.wildfly.management.client.impl.StreamUtils.safeClose;
import static org.wildfly.management.client.helpers.ClientConstants.OUTCOME;
import static org.wildfly.management.client.helpers.ClientConstants.RESULT;
import static org.wildfly.management.client.helpers.ClientConstants.SUCCESS;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.management.client.ManagementConnection;
import org.wildfly.management.client.OperationStreamAttachments;

/**
 * @author Emanuel Muckenhuber
 */
public class BasicMgmtClientUnitTestCase extends AbstractMgmtClientTestCase {

    private static final ModelNode BASIC_OPERATION;
    private static final ModelNode SUCCESS_FULL_RESPONSE;

    static {
        final ModelNode operation = new ModelNode();
        operation.get("op").set("read-attribute");
        operation.get("address").add("test", "test");
        operation.protect();

        final ModelNode response = new ModelNode();
        response.get("result").set("true");
        response.get("outcome").set("success");
        response.protect();

        SUCCESS_FULL_RESPONSE = response;
        BASIC_OPERATION = operation;
    }

    @Test
    public void testBasicRequest() throws Exception {
        server.setInitialHandler(new TestServer.AbstractMessageHandler() {
            @Override
            public TestServer.TestMessageHandler handleMessage(DataInput dataInput, TestServer.TestMessageHandlerContext context) {
                context.sendResponse(this);
                return null;
            }

            @Override
            public void writeMessage(DataOutput os) throws IOException {
                SUCCESS_FULL_RESPONSE.writeExternal(os);
            }
        });

        final ModelNode operation = BASIC_OPERATION;
        final ManagementConnection connection = openConnection();
        try {
            final ModelNode result = connection.execute(operation);
            Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());
            Assert.assertEquals("true", result.get(RESULT).asString());
        } finally {
            safeClose(connection);
        }

    }

    @Test
    public void testConnectionClosed() throws Exception {
        final ManagementConnection connection = openConnection();
        try {
            connection.close();
            try {
                connection.execute(BASIC_OPERATION);
                Assert.fail();
            } catch (IOException ok) {

            }
        } finally {
            safeClose(connection);
        }
    }

    @Test
    public void testFailedRequest() throws IOException {

        server.setInitialHandler(new TestServer.TestMessageHandler() {
            @Override
            public TestServer.TestMessageHandler handleMessage(DataInput dataInput, TestServer.TestMessageHandlerContext context) {
                ManagementClientChannelReceiver.safeWriteErrorResponse(context.getChannel(), context.getRequestHeader(), new IOException("failed"));
                return null;
            }
        });

        final ManagementConnection connection = openConnection();
        try {
            connection.execute(BASIC_OPERATION);
            Assert.fail();
        } catch (IOException ok) {
            //OK
        } finally {
            safeClose(connection);
        }
    }

    @Test
    public void testFailedCancel() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        server.setInitialHandler(new TestServer.TestMessageHandler() {
            @Override
            public TestServer.TestMessageHandler handleMessage(DataInput dataInput, TestServer.TestMessageHandlerContext context) {
                // don't respond to the mgmt request
                latch.countDown();
                return new TestServer.TestMessageHandler() {
                    @Override
                    public TestServer.TestMessageHandler handleMessage(DataInput input, TestServer.TestMessageHandlerContext context) {
                        ManagementClientChannelReceiver.safeWriteErrorResponse(context.getChannel(), context.getRequestHeader(), new IOException("failed"));
                        return null;
                    }
                };
            }
        });

        final ManagementConnection connection = openConnection();
        try {
            final Future<ModelNode> futureResponse = connection.executeAsync(BASIC_OPERATION);
            latch.await();
            futureResponse.cancel(false);
            futureResponse.get();
            Assert.fail();
        } catch (ExecutionException ok) {
            // OK
        } finally {
            safeClose(connection);
        }
    }

    @Test
    public void testCancelOperation() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final TestServer.TestMessageHandler cancellationHandler = new TestServer.AbstractMessageHandler() {
            @Override
            public TestServer.TestMessageHandler handleMessage(DataInput dataInput, TestServer.TestMessageHandlerContext context) {
                context.sendResponse(this);
                latch.countDown();
                return null;
            }

            @Override
            public void writeMessage(DataOutput os) throws IOException {
                //
            }
        };

        server.setInitialHandler(new TestServer.AbstractMessageHandler() {
            @Override
            public TestServer.TestMessageHandler handleMessage(final DataInput dataInput, final TestServer.TestMessageHandlerContext context) {
                final TestServer.TestMessageWriter writer = this;
                context.executeAsync(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        context.sendResponse(writer);
                    }
                });
                return cancellationHandler;
            }

            @Override
            public void writeMessage(DataOutput os) throws IOException {
                SUCCESS_FULL_RESPONSE.writeExternal(os);
            }
        });

        final ManagementConnection connection = openConnection();
        try {
            final Future<ModelNode> result = connection.executeAsync(BASIC_OPERATION);
            Assert.assertFalse(result.isDone());
            result.cancel(false);
            Assert.assertTrue(result.isCancelled());

        } finally {
            safeClose(connection);
        }
    }

    @Test
    public void testGetInputStream() throws IOException {
        server.setInitialHandler(new TestServer.TestMessageHandler() {
            @Override
            public TestServer.TestMessageHandler handleMessage(final DataInput dataInput, final TestServer.TestMessageHandlerContext initialContext) {
                // Request the input stream
                initialContext.sendRequest(ManagementProtocol.GET_INPUTSTREAM_REQUEST, new TestServer.TestMessageWriter() {
                    @Override
                    public void writeMessage(DataOutput os) throws IOException {
                        os.write(ManagementProtocol.PARAM_INPUTSTREAM_INDEX);
                        os.writeInt(1);
                    }
                });
                // Check the input stream and write the successful response
                return new TestServer.AbstractMessageHandler() {
                    @Override
                    public TestServer.TestMessageHandler handleMessage(DataInput dataInput, TestServer.TestMessageHandlerContext ignored) {
                        try {
                            expectHeader(ManagementProtocol.PARAM_INPUTSTREAM_LENGTH, dataInput.readByte());
                            final int length = dataInput.readInt();
                            if (length != 1) {
                                throw new RuntimeException();
                            }
                            expectHeader(ManagementProtocol.PARAM_INPUTSTREAM_CONTENTS, dataInput.readByte());
                            final byte data = dataInput.readByte();
                            if (data != 0x01) {
                                throw new RuntimeException();
                            }
                            expectHeader(ManagementProtocol.RESPONSE_END, dataInput.readByte());
                        } catch (Exception e) {
                            ManagementClientChannelReceiver.safeWriteErrorResponse(initialContext.getChannel(), initialContext.getRequestHeader(), e);
                        }
                        initialContext.sendResponse(this, initialContext);
                        return null;
                    }

                    @Override
                    public void writeMessage(DataOutput os) throws IOException {
                        SUCCESS_FULL_RESPONSE.writeExternal(os);
                    }
                };
            }
        });

        final ManagementConnection connection = openConnection();
        try {
            connection.execute(BASIC_OPERATION, new OperationStreamAttachments() {
                @Override
                public int getNumberOfAttachedStreams() {
                    return 1;
                }

                @Override
                public long getInputStreamSize(int i) {
                    return 1;
                }

                @Override
                public InputStream getInputStream(int i) throws IOException {
                    final byte[] data = new byte[1];
                    data[0] = 0x01;
                    return new ByteArrayInputStream(data);
                }
            });
        } finally {
            safeClose(connection);
        }
    }

    @Test
    public void testGetInputStreamFailure() throws IOException {
        server.setInitialHandler(new TestServer.TestMessageHandler() {
            @Override
            public TestServer.TestMessageHandler handleMessage(DataInput dataInput, final TestServer.TestMessageHandlerContext initialContext) {
                // Request the input stream
                initialContext.sendRequest(ManagementProtocol.GET_INPUTSTREAM_REQUEST, new TestServer.TestMessageWriter() {
                    @Override
                    public void writeMessage(DataOutput os) throws IOException {
                        os.write(ManagementProtocol.PARAM_INPUTSTREAM_INDEX);
                        os.writeInt(1);
                    }
                });
                // Send response with initial context
                return new TestServer.AbstractMessageHandler() {
                    @Override
                    public TestServer.TestMessageHandler handleMessage(DataInput dataInput, TestServer.TestMessageHandlerContext response) {
                        try {
                            final ManagementResponseHeader responseHeader = (ManagementResponseHeader) response.getRequestHeader();
                            if (!responseHeader.isFailed()) {
                                throw new RuntimeException();
                            }
                        } catch (Exception e) {
                            ManagementClientChannelReceiver.safeWriteErrorResponse(initialContext.getChannel(), initialContext.getRequestHeader(), e);
                        }
                        initialContext.sendResponse(this, initialContext);
                        return null;
                    }

                    @Override
                    public void writeMessage(DataOutput os) throws IOException {
                        SUCCESS_FULL_RESPONSE.writeExternal(os);
                    }
                };
            }
        });

        final ManagementConnection connection = openConnection();
        try {

            connection.execute(BASIC_OPERATION, new OperationStreamAttachments() {
                @Override
                public int getNumberOfAttachedStreams() {
                    return 1;
                }

                @Override
                public long getInputStreamSize(int i) {
                    return 4096L;
                }

                @Override
                public InputStream getInputStream(int i) throws IOException {
                    throw new IOException("cannot open stream");
                }
            });

        } finally {
            safeClose(connection);
        }

    }

}
