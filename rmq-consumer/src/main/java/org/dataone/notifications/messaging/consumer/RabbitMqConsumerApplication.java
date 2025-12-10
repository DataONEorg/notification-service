package org.dataone.notifications.messaging.consumer;

import org.dataone.notifications.NsConfig;
import org.dataone.notifications.messaging.config.RabbitMqConnectionManager;
import org.dataone.notifications.messaging.config.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstraps the RabbitMQ consumer alongside the Jakarta EE runtime.
 */
public class RabbitMqConsumerApplication {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConsumerApplication.class);

    private SubscriptionEventConsumer consumer;

    public void start() {
        RabbitMqProperties props = RabbitMqProperties.from(NsConfig.getConfig());
        SubscriptionMessageProcessor processor = new SubscriptionMessageProcessor();
        consumer = new SubscriptionEventConsumer(props,
            new RabbitMqConnectionManager(props), processor);
        consumer.start();
        log.info("Subscription consumer started");
    }

    public void stop() {
        if (consumer != null) {
            log.info("Stopping subscription consumer");
            consumer.stop();
        }
    }
}
