package org.wildfly.management.client;

/**
 * A filter to let {@link NotificationHandler} filters out notifications they are not interested to handle.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2013 Red Hat inc.
 */
public interface NotificationFilter {

    boolean isNotificationEnabled(Notification notification);

    NotificationFilter ALL = new NotificationFilter() {
        @Override
        public boolean isNotificationEnabled(Notification notification) {
            return true;
        }
    };
}