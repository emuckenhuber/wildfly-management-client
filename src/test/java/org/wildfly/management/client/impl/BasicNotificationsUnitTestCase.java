package org.wildfly.management.client.impl;

import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.dmr.ModelNode;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.MessageOutputStream;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.management.client.ManagementConnection;
import org.wildfly.management.client.Notification;
import org.wildfly.management.client.NotificationFilter;
import org.wildfly.management.client.NotificationHandler;

/**
 * @author Emanuel Muckenhuber
 */
public class BasicNotificationsUnitTestCase extends AbstractMgmtClientTestCase {

    static final ModelNode ADDRESS = new ModelNode().setEmptyList();

    @Test
    public void testBasicNotifications() throws Exception {

        final ServerHandler serverHandler = new ServerHandler();
        server.setInitialHandler(serverHandler);

        final CountDownLatch latch = new CountDownLatch(10);
        final NotificationHandler notificationHandler = new NotificationHandler() {
            @Override
            public void handleNotification(Notification notification) {
                latch.countDown();
            }
        };

        final ManagementConnection connection = openConnection();
        try {
            final ManagementConnection.NotificationRegistration registration = connection.registerNotificationHandler(ADDRESS, notificationHandler, NotificationFilter.ALL);
            try {
                Assert.assertEquals(1, serverHandler.remoteListeners.size());
                for (int i = 0; i < 10; i++) {
                    final Notification notification = new Notification("test", new ModelNode(), "test message " + i);
                    serverHandler.sendNotification(notification);
                }
                latch.await();
            } finally {
                registration.unregister();
            }
            Assert.assertEquals(0, serverHandler.remoteListeners.size());
        } finally {
            StreamUtils.safeClose(connection);
        }
    }

    static class ServerHandler implements TestServer.TestMessageHandler {
        private final ConcurrentMap<Integer, RemoteNotificationSender> remoteListeners = new ConcurrentHashMap<>();

        @Override
        public TestServer.TestMessageHandler handleMessage(DataInput dataInput, TestServer.TestMessageHandlerContext context) throws IOException {
            final ManagementProtocolHeader header = context.getRequestHeader();
            if (header.getType() == ManagementProtocol.TYPE_RESPONSE) {
                // skip all responses from the client
            } else {
                final ManagementRequestHeader request = (ManagementRequestHeader) header;
                final Channel channel = context.getChannel();
                final int operationID = request.getBatchId();
                final byte type = request.getOperationId();
                switch (type) {
                    // Register notification handler
                    case ManagementProtocol.REGISTER_NOTIFICATION_HANDLER_REQUEST: {
                        final RemoteNotificationSender sender = new RemoteNotificationSender(operationID, channel);
                        if (remoteListeners.putIfAbsent(operationID, sender) == null) {
                            channel.addCloseHandler(new CloseHandler<Channel>() {
                                @Override
                                public void handleClose(Channel channel, IOException e) {
                                    remoteListeners.remove(operationID);
                                }
                            });
                        }
                        ManagementClientChannelReceiver.writeEmptyResponse(channel, request);
                        break;
                    // unregister notification handler
                    } case ManagementProtocol.UNREGISTER_NOTIFICATION_HANDLER_REQUEST: {
                        remoteListeners.remove(operationID);
                        ManagementClientChannelReceiver.writeEmptyResponse(channel, request);
                        break;
                    } default: {
                        throw new IllegalStateException();
                    }
                }
            }
            return this;
        }

        void sendNotification(final Notification notification) throws IOException {
            for (final RemoteNotificationSender sender : remoteListeners.values()) {
                sender.sendNotification(notification);
            }
        }

    }

    static class RemoteNotificationSender {

        private final int operationID;
        private final Channel channel;

        RemoteNotificationSender(int operationID, Channel channel) {
            this.operationID = operationID;
            this.channel = channel;
        }

        protected void sendNotification(final Notification notification) {
            final ManagementRequestHeader header = new ManagementRequestHeader(ManagementProtocol.VERSION, 0, operationID, ManagementProtocol.HANDLE_NOTIFICATION_REQUEST);
            try {
                final ModelNode model = notification.toModelNode();
                final MessageOutputStream os = channel.writeMessage();
                try {
                    final DataOutputStream dos = new DataOutputStream(os);
                    header.write(dos);
                    model.writeExternal(dos);
                } finally {
                    StreamUtils.safeClose(os);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
