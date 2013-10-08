package org.wildfly.management.client.impl;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.wildfly.management.client.ManagementConnection;
import org.xnio.IoFuture;

/**
 * @author Emanuel Muckenhuber
 */
class ManagementConnectionFuture implements Future<ManagementConnection> {

    private final long defaultConnectionTimeout;
    private final IoFuture<ManagementConnection> future;
    private final WrapperCallbackHandler wrapperHandler;

    ManagementConnectionFuture(WrapperCallbackHandler callbackHandler, IoFuture<ManagementConnection> future, long defaultConnectionTimeout) {
        this.wrapperHandler = callbackHandler;
        this.future = future;
        this.defaultConnectionTimeout = defaultConnectionTimeout;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel().getStatus() == IoFuture.Status.CANCELLED;
    }

    @Override
    public boolean isCancelled() {
        return future.getStatus() == IoFuture.Status.CANCELLED;
    }

    @Override
    public boolean isDone() {
        return future.getStatus() == IoFuture.Status.DONE;
    }

    @Override
    public ManagementConnection get() throws InterruptedException, ExecutionException {
        try {
            return get(defaultConnectionTimeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public ManagementConnection get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        final long timeoutMillis = unit.toMillis(timeout);
        IoFuture.Status status = future.await(timeoutMillis, TimeUnit.MILLISECONDS);
        while (status == IoFuture.Status.WAITING) {
            if (wrapperHandler.isInCall()) {
                // If there is currently an interaction with the user just wait again.
                status = future.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } else {
                long lastInteraction = wrapperHandler.getCallFinished();
                if (lastInteraction > 0) {
                    long now = System.currentTimeMillis();
                    long timeSinceLast = now - lastInteraction;
                    if (timeSinceLast < timeoutMillis) {
                        // As this point we are setting the timeout based on the time of the last interaction
                        // with the user, if there is any time left we will wait for that time but dont wait for
                        // a full timeout.
                        status = future.await(timeoutMillis - timeSinceLast, TimeUnit.MILLISECONDS);
                    } else {
                        status = null;
                    }
                } else {
                    status = null; // Just terminate status processing.
                }
            }
        }
        if (status == IoFuture.Status.DONE) {
            try {
                return future.get();
            } catch (IOException e) {
                throw new ExecutionException(e);
            }
        }
        if (status == IoFuture.Status.FAILED) {
            throw new ExecutionException(future.getException());
        }
        future.cancel();
        throw new TimeoutException("connection timed out");
    }

    static final class WrapperCallbackHandler implements CallbackHandler {

        private volatile boolean inCall = false;
        private volatile long callFinished = -1;
        private final CallbackHandler wrapped;

        WrapperCallbackHandler(final CallbackHandler toWrap) {
            this.wrapped = toWrap;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            inCall = true;
            try {
                wrapped.handle(callbacks);
            } finally {
                // Set the time first so if a read is made between these two calls it will say inCall=true until
                // callFinished is set.
                callFinished = System.currentTimeMillis();
                inCall = false;
            }
        }

        boolean isInCall() {
            return inCall;
        }

        long getCallFinished() {
            return callFinished;
        }
    }
}
