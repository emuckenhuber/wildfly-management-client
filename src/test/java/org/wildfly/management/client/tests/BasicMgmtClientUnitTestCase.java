package org.wildfly.management.client.tests;

import static org.wildfly.management.client.impl.StreamUtils.safeClose;
import static org.wildfly.management.client.helpers.ClientConstants.OUTCOME;
import static org.wildfly.management.client.helpers.ClientConstants.RESULT;
import static org.wildfly.management.client.helpers.ClientConstants.SUCCESS;

import java.io.DataOutput;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.management.client.ManagementConnection;
import org.wildfly.management.client.impl.ManagementProtocol;

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
            public TestServer.TestMessageHandler handleMessage(TestServer.TestMessageHandlerContext context) {
                context.writeMessage(this);
                return null;
            }

            @Override
            public void writeMessage(DataOutput os) throws IOException {
                os.write(ManagementProtocol.PARAM_RESPONSE);
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
    public void testCancelOperation() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final TestServer.TestMessageHandler cancellationHandler = new TestServer.AbstractMessageHandler() {
            @Override
            public TestServer.TestMessageHandler handleMessage(TestServer.TestMessageHandlerContext context) {
                context.writeMessage(this);
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
            public TestServer.TestMessageHandler handleMessage(final TestServer.TestMessageHandlerContext context) {
                final TestServer.TestMessageWriter writer = this;
                context.executeAsync(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        context.writeMessage(writer);
                    }
                });
                return cancellationHandler;
            }

            @Override
            public void writeMessage(DataOutput os) throws IOException {
                os.write(ManagementProtocol.PARAM_RESPONSE);
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

}
