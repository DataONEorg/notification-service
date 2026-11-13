package org.dataone.notifications.eventsource;

import org.dataone.notifications.NsConfig;
import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.event.RabbitMqConnection;
import org.dataone.notifications.event.RabbitMqProperties;
import org.dataone.notifications.resource.SubscriptionResourceType;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * The class to get/update subscription information for a pid. Also, it will submit the event
 * information to RabbitMQ.
 */
public class SubscriptionProcessor {
    private static volatile SubscriptionProcessor processor;
    private static RabbitMqProperties prorperties = RabbitMqProperties.from(NsConfig.getConfig());
    private static RabbitMqConnection rabbitMqConnection;

    /**
     * Get the singleton instance of the class
     * @return an instance of this class
     * @throws IOException
     * @throws TimeoutException
     */
    public SubscriptionProcessor getInstance() throws IOException, TimeoutException {
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
     * @throws IOException
     * @throws TimeoutException
     */
    private SubscriptionProcessor() throws IOException, TimeoutException {
        rabbitMqConnection = new RabbitMqConnection(prorperties);
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
