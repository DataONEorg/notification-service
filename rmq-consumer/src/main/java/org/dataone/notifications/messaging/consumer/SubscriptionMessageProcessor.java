package org.dataone.notifications.messaging.consumer;

import org.dataone.notifications.messaging.SubscriptionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder processor invoked after a RabbitMQ message is consumed.
 */
public class SubscriptionMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionMessageProcessor.class);

    public void process(SubscriptionEvent event) {
        log.info("Processing subscription event: resourceType={}, pid={}",
            event.resourceType(), event.pid());
        // TODO implement real business logic; e.g. send emails, update DB, etc.
    }
}
