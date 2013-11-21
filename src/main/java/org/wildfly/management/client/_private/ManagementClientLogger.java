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

package org.wildfly.management.client._private;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.WARN;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.remoting3.Channel;

/**
 * @author Emanuel Muckenhuber
 */
@MessageLogger(projectCode = "WFLY")
public interface ManagementClientLogger extends BasicLogger {

    ManagementClientLogger ROOT_LOGGER = Logger.getMessageLogger(ManagementClientLogger.class, ManagementClientLogger.class.getPackage().getName());

    @LogMessage(level = ERROR)
    @Message(id = 12102, value = "Failed to close resource %s")
    void failedToCloseResource(@Cause Throwable cause, Object resource);

    @LogMessage(level = WARN)
    @Message(id = 12118, value = "No such request (%d) associated with channel %s")
    void noSuchRequest(int requestId, Channel channel);

    /**
     * Logs a warn message indicating that a controller client wasn't closed properly.
     *
     * @param allocationStackTrace the allocation stack trace
     */
    @LogMessage(level = WARN)
    @Message(id = 10600, value = "Closing leaked controller client")
    void leakedControllerClient(@Cause Throwable allocationStackTrace);

}
