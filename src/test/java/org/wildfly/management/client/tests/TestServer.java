package org.wildfly.management.client.tests;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.OpenListener;
import org.xnio.OptionMap;

/**
 * @author Emanuel Muckenhuber
 */
class TestServer implements OpenListener, Channel.Receiver {

    private final ExecutorService executorService;
    public TestServer(ExecutorService executorService) {
        this.executorService = executorService;
    }

    void register(final Endpoint endpoint) throws IOException {
        endpoint.registerService("management", this, OptionMap.EMPTY);
    }

    @Override
    public void channelOpened(Channel channel) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void registrationTerminated() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void handleError(Channel channel, IOException error) {
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleEnd(Channel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handleMessage(Channel channel, MessageInputStream message) {

    }
}
