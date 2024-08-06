package org.dataone.notifications.api.data;


import org.dataone.notifications.api.resource.NsRecord;
import org.dataone.notifications.api.resource.ResourceType;

import java.util.List;

/**
 * An interface for authenticating and authorizing users.
 */
public interface DataProvider {

    /**
     * Get a list of subscriptions for a given subject and resource type.
     *
     * @param subject the subject of the user
     * @param resourceType the type of resource
     * @return a list of pids of the resources to which the user is subscribed
     */
    List<String> getSubscriptions(String subject, ResourceType resourceType);

    /**
     * Add a subscription for a given subject and resource type.
     *
     * @param subject the subject of the user
     * @param resourceType the type of resource
     * @param pid the pid of the resource to which the user is subscribed
     */
    void addSubscription(String subject, ResourceType resourceType, String pid);

    /**
     * Remove a subscription for a given subject and resource type.
     *
     * @param subject the subject of the user
     * @param resourceType the type of resource
     * @param pid the pid of the resource to which the user is subscribed
     */
    void removeSubscription(String subject, ResourceType resourceType, String pid);

    /**
     * Remove all subscriptions for a given subject and resource type.
     *
     * @param subject the subject of the user
     * @param resourceType the type of resource
     */
    void removeAllSubscriptions(String subject, ResourceType resourceType);

    /**
     * Remove all subscriptions for a given subject.
     *
     * @param subject the subject of the user
     */
    void removeAllSubscriptions(String subject);
}
