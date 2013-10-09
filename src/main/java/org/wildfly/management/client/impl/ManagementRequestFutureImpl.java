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
class ManagementRequestFutureImpl implements Future<ModelNode> {

    private final IoFuture<ModelNode> future;

    ManagementRequestFutureImpl(IoFuture<ModelNode> future) {
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
