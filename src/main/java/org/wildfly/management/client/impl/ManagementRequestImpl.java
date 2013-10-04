package org.wildfly.management.client.impl;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.dmr.ModelNode;
import org.xnio.IoFuture;

/**
 * @author Emanuel Muckenhuber
 */
class ManagementRequestImpl implements Future<ModelNode> {

    private final IoFuture<ModelNode> future;

    ManagementRequestImpl(IoFuture<ModelNode> future) {
        this.future = future;
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
        return future.getStatus() != IoFuture.Status.WAITING;
    }

    @Override
    public ModelNode get() throws InterruptedException, ExecutionException {
        try {
            return future.get();
        } catch (IOException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public ModelNode get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        final long end = System.currentTimeMillis() + unit.toMillis(timeout);
        try {
            while (future.getStatus() == IoFuture.Status.WAITING) {
                final long wait = end - System.currentTimeMillis();
                if (wait > 0) {
                    future.await(wait, TimeUnit.MILLISECONDS);
                }
            }
            if (future.getStatus() == IoFuture.Status.DONE) {
                return future.get();
            }
            throw new TimeoutException();
        } catch (IOException e) {
            throw new ExecutionException(e);
        }
    }
}
