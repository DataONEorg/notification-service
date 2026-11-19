package org.dataone.notifications.event.notifier;

import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.exception.NotificationDeliveryException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;

public interface NotificationListener {

    /**
     * Handle a notification for a given subscriber, resource type, and pid.
     *
     * @param event An NsEventMessage containing the event information to use in notifications.
     * @return  A string containing the subscriber's identifier, if the notification was
     *                     successfully sent. If the notification failed, an exception is thrown.
     * @throws NotificationDeliveryException delivery cannot be completed
     */
    String handleNotification(NsEventMessage.NsEvent event)
        throws UnsupportedResourceTypeException, NotificationDeliveryException;
}
