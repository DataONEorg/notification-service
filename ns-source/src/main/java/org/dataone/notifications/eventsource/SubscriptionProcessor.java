package org.dataone.notifications.eventsource;

import org.dataone.notifications.resource.SubscriptionResourceType;

import java.util.List;

/**
 * The class to get/update subscription information for a pid. Also, it will submit the event
 * information to RabbitMQ.
 */
public class SubscriptionProcessor {

    /**
     * Get the all subscription ids for a given pid for the subscription
     * @param pid  the object id for the subscription
     * @param type  the subscription resource type for the object
     * @return the list of subscription ids; null will return if there are no subscriptions
     */
    public List<String> getSubscriptions(String pid, SubscriptionResourceType type) {
        return null;
    }

    /**
     * Update the subscription to a new pid from an old one.
     * @param pid  the old subscription object
     * @param newPid  the new subscription object
     * @param type  the type of the subscription
     */
    public void updateSubscriptions(String pid, String newPid, SubscriptionResourceType type) {

    }
}
