package org.dataone.notifications;

import org.dataone.notifications.exception.NotificationDeliveryException;

public interface NotificationListener {

    /**
     * Handle a notification for a given subscriber, resource type, and pid.
     *
     * @param subscriber   The subscriber's identifier (e.g., ORCID).
     * @param resourceType The type of resource associated with the notification. UNCHECKED - used
     *                     verbatim in notification messaging. Should be validated before calling
     *                     this method.
     * @param pid          The persistent identifier of the resource. UNCHECKED - used verbatim in
     *                     notification messaging. Should be validated before calling this method.
     * @return             A string containing the subscriber's identifier, if the notification was
     *                     successfully sent. If the notification failed, an exception is thrown.
     * @throws NotificationDeliveryException if the subscriber's identifier is invalid or
     *                                       notification delivery fails
     */
    String handleNotification(String subscriber, String resourceType, String pid)
        throws NotificationDeliveryException;
}
