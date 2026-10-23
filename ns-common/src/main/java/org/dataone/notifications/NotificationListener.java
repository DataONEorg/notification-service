package org.dataone.notifications;

public interface NotificationListener {
    /**
     * Handle a notification for a given subscriber, resource type, and pid.
     *
     * @param subscriber   The subscriber's identifier (e.g., ORCID).
     * @param resourceType The type of resource associated with the notification.
     * @param pid          The persistent identifier of the resource.
     * @return A string indicating the result of the notification handling.
     */
    String handleNotification(String subscriber, String resourceType, String pid);
}
