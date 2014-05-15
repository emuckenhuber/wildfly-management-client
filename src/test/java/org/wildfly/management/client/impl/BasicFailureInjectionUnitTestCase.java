package org.wildfly.management.client.impl;

import java.io.DataInput;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.management.client.ManagementConnection;

/**
 * @author Emanuel Muckenhuber
 */
@RunWith(BMUnitRunner.class)
public class BasicFailureInjectionUnitTestCase extends AbstractMgmtClientTestCase {

    @Test
    @BMRule(name="throw IOException on 1st write",
            targetClass = "ManagementConnectionImpl$ExecuteRequest", targetMethod = "writeRequest",
            action = "throw new java.lang.RuntimeException()")
    public void testFailureOnFirstRequest() throws IOException {
        final ManagementConnection connection = openConnection();
        try {
            final ModelNode operation = new ModelNode();
            connection.execute(operation);
            Assert.fail();
        } catch (IOException expected) {
            // OK
        } finally {
            StreamUtils.safeClose(connection);
        }
    }

    @Test
    @BMRule(name="throw IOException on cancel",
            targetClass = "ManagementConnectionImpl$CancelRequest", targetMethod = "writeRequest",
            action = "throw new java.lang.RuntimeException()")
    public void testFailureOnCancel() throws IOException {
        final CountDownLatch latch = new CountDownLatch(1);
        server.setInitialHandler(new TestServer.TestMessageHandler() {

            @Override
            public TestServer.TestMessageHandler handleMessage(DataInput dataInput, TestServer.TestMessageHandlerContext context) {
                // Just ignore the request
                latch.countDown();
                return null;
            }
        });

        final ManagementConnection connection = openConnection();
        try {
            final ModelNode operation = new ModelNode();
            final Future<ModelNode> futureResult = connection.executeAsync(operation);
            latch.await(); // Wait for the message being delivered
            futureResult.cancel(false);
            futureResult.get();
            Assert.fail();
        } catch (Exception ok) {
            // OK
        } finally {
            StreamUtils.safeClose(connection);
        }
    }

}
