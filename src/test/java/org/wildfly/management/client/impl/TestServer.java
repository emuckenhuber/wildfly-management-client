package org.wildfly.management.client.impl;

import static org.wildfly.management.client.impl.ManagementProtocol.VERSION;

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.xnio.OptionMap;

/**
 * @author Emanuel Muckenhuber
 */
class TestServer extends ManagementClientChannelReceiver implements OpenListener, Channel.Receiver, Closeable {

    private Registration registration;

    private AtomicInteger requestCounter = new AtomicInteger(1);
    private volatile TestMessageHandler handler;
    private final ExecutorService executorService;

    public TestServer(ExecutorService executorService) {
        this.executorService = executorService;
    }

    void register(final Endpoint endpoint) throws IOException {
        registration = endpoint.registerService("management", this, OptionMap.EMPTY);
    }

    void setInitialHandler(TestMessageHandler newHandler) {
        assert handler == null;
        handler = newHandler;
    }

    @Override
    public void channelOpened(Channel channel) {
        channel.receiveMessage(this);
    }

    @Override
    public void registrationTerminated() {
        //
    }

    @Override
    protected synchronized void handleMessage(final Channel channel, final DataInput input, final ManagementProtocolHeader header) {
        final TestMessageHandler handler = this.handler;
        if (handler != null) {
            try {
                this.handler = handler.handleMessage(input, new TestMessageHandlerContext() {
                    @Override
                    public void executeAsync(Runnable r) {
                        executorService.execute(r);
                    }

                    @Override
                    public ManagementProtocolHeader getRequestHeader() {
                        return header;
                    }

                    @Override
                    public Channel getChannel() {
                        return channel;
                    }

                    @Override
                    public void sendResponse(TestMessageWriter writer) {
                        sendResponse(writer, this);
                    }

                    @Override
                    public void sendResponse(TestMessageWriter writer, TestMessageHandlerContext context) {
                        final ManagementResponseHeader response = ManagementResponseHeader.create((ManagementRequestHeader) context.getRequestHeader());
                        try {
                            final MessageOutputStream os = context.getChannel().writeMessage();
                            try {
                                final DataOutputStream dos = new DataOutputStream(os);
                                response.write(dos);
                                dos.write(ManagementProtocol.PARAM_RESPONSE);
                                writer.writeMessage(dos);
                                dos.write(ManagementProtocol.PARAM_END);
                                dos.write(ManagementProtocol.REQUEST_END);
                                dos.close();
                            } finally {
                                StreamUtils.safeClose(os);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void sendRequest(byte operationType, TestMessageWriter writer) {
                        final ManagementRequestHeader requestHeader = new ManagementRequestHeader(VERSION, requestCounter.incrementAndGet(), ((ManagementRequestHeader) header).getBatchId(), operationType);
                        try {
                            final MessageOutputStream os = channel.writeMessage();
                            try {
                                final DataOutputStream dos = new DataOutputStream(os);
                                requestHeader.write(dos);
                                writer.writeMessage(dos);
                                dos.write(ManagementProtocol.REQUEST_END);
                                dos.close();
                            } finally {
                                StreamUtils.safeClose(os);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                ManagementClientChannelReceiver.safeWriteErrorResponse(channel, header, e);
            }
        }
    }

    @Override
    protected Channel.Receiver next() {
        return this;
    }

    @Override
    public void close() throws IOException {
        StreamUtils.safeClose(registration);
    }

    static interface TestMessageHandler {

        TestMessageHandler handleMessage(DataInput dataInput, TestMessageHandlerContext context) throws IOException;

    }

    static interface TestMessageHandlerContext {

        ManagementProtocolHeader getRequestHeader();
        Channel getChannel();

        void executeAsync(Runnable r);

        void sendResponse(TestMessageWriter writer);
        void sendResponse(TestMessageWriter writer, TestMessageHandlerContext context);
        void sendRequest(byte operationType, TestMessageWriter writer);

    }

    static interface TestMessageWriter {

        void writeMessage(DataOutput os) throws IOException;

    }

    abstract static class AbstractMessageHandler implements TestMessageHandler, TestMessageWriter {

    }

}
