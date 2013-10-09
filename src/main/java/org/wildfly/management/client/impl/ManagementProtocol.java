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

/**
 * @author John Bailey
 */
interface ManagementProtocol {

    // Headers
    byte[] SIGNATURE = {Byte.MAX_VALUE, Byte.MIN_VALUE, Byte.MAX_VALUE, Byte.MIN_VALUE};
    int VERSION_FIELD = 0x00; // The version field header
    int VERSION = 1; // The current protocol version

    byte TYPE = 0x1;
    byte TYPE_REQUEST = 0x2;
    byte TYPE_RESPONSE = 0x3;
    byte TYPE_BYE_BYE = 0x4;
    byte TYPE_PING = 0x5;
    byte TYPE_PONG = 0x6;

    byte REQUEST_ID = 0x10;
    byte BATCH_ID = 0x11;
    byte OPERATION_ID = 0x12;
    byte ONE_WAY = 0x13;
    byte REQUEST_BODY = 0x14;
    byte REQUEST_END = 0x15;

    byte RESPONSE_ID = 0x20;
    byte RESPONSE_TYPE = 0x21;
    byte RESPONSE_BODY = 0x22;
    byte RESPONSE_ERROR = 0x23;
    byte RESPONSE_END = 0x24;
    byte EXECUTE_ASYNC_CLIENT_REQUEST = 0x45;
    byte EXECUTE_CLIENT_REQUEST = 0x46;
    byte EXECUTE_TX_REQUEST = 0x47;
    byte HANDLE_REPORT_REQUEST = 0x48;
    byte GET_INPUTSTREAM_REQUEST = 0x4C;
    byte CANCEL_ASYNC_REQUEST = 0x4D;
    byte COMPLETE_TX_REQUEST = 0x4E;
    byte PARAM_END = 0x60;
    byte PARAM_OPERATION = 0x61;
    byte PARAM_MESSAGE_SEVERITY = 0x62;
    byte PARAM_MESSAGE = 0x63;
    byte PARAM_RESPONSE = 0x64;
    byte PARAM_INPUTSTREAMS_LENGTH = 0x65;
    byte PARAM_INPUTSTREAM_INDEX = 0x66;
    byte PARAM_INPUTSTREAM_LENGTH = 0x67;
    byte PARAM_INPUTSTREAM_CONTENTS = 0x68;
    //byte PARAM_PREPARED = 0x69;
    byte PARAM_COMMIT = 0x70;
    byte PARAM_ROLLBACK = 0x71;
    // The tx response params
    byte PARAM_OPERATION_FAILED = 0x49;
    byte PARAM_OPERATION_COMPLETED = 0x4A;
    byte PARAM_OPERATION_PREPARED = 0x4B;
    byte REGISTER_NOTIFICATION_HANDLER_REQUEST = 0x50;
    byte UNREGISTER_NOTIFICATION_HANDLER_REQUEST = 0x51;
    byte HANDLE_NOTIFICATION_REQUEST = 0x52;
}
