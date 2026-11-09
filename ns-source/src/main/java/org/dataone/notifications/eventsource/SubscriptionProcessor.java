package org.dataone.notifications.eventsource;

import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.resource.SubscriptionResourceType;

import java.util.List;

/**
 * The class to get/update subscription information for a pid. Also, it will submit the event
 * information to RabbitMQ.
 */
public class SubscriptionProcessor {
    private static volatile SubscriptionProcessor processor;

    /**
     * Get the singleton instance of the class
     * @return an instance of this class
     */
    public SubscriptionProcessor getInstance() {
        if (processor == null) {
            synchronized (SubscriptionProcessor.class) {
                if (processor == null) {
                    processor = new SubscriptionProcessor();
                }
            }
        }
        return processor;
    }

    /**
     * Default constructor
     */
    private SubscriptionProcessor() {

    }

    /**
     * Process the event message
     * @param message  the message need to be processed
     */
    public void process(NsEventMessage message) {

    }

    /**
     * Get the all subscription ids for a given pid for the subscription
     * @param pid  the object id for the subscription
     * @param type  the subscription resource type for the object
     * @return the list of subscription ids; null will return if there are no subscriptions
     */
    private List<String> getSubscriptions(String pid, SubscriptionResourceType type) {
        return null;
    }

    /**
     * Update the subscription to a new pid from an old one.
     * @param pid  the old subscription object
     * @param newPid  the new subscription object
     * @param type  the type of the subscription
     */
    private void updateSubscriptions(String pid, String newPid, SubscriptionResourceType type) {

    }

    private void submit(NsEventMessage message) {

    }
}
