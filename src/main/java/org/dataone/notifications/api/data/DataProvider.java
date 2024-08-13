package org.dataone.notifications.api.data;


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
     * @return the Subscription containing the successful subscription details
     */
    Subscription addSubscription(String subject, ResourceType resourceType, String pid);

    /**
     * Delete one or more subscriptions for a given subject and resource type.
     *
     * @param subject the subject of the user
     * @param resourceType the type of resource
     * @param pidList a List of the resource pids to which the user is already subscribed
     * @return a Subscription object containing the successful unsubscribe details
     */
    Subscription deleteSubscriptions(
        String subject, ResourceType resourceType, List<String> pidList);
}
