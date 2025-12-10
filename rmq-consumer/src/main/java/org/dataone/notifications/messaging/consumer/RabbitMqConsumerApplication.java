package org.dataone.notifications.messaging.consumer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import org.dataone.notifications.NsConfig;
import org.dataone.notifications.messaging.config.RabbitMqConnectionManager;
import org.dataone.notifications.messaging.config.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstraps the RabbitMQ consumer alongside the Jakarta EE runtime.
 */
@Startup
@Singleton
public class RabbitMqConsumerApplication {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConsumerApplication.class);

    private SubscriptionEventConsumer consumer;

    @PostConstruct
    public void start() {
        RabbitMqProperties props = RabbitMqProperties.from(NsConfig.getConfig());
        SubscriptionMessageProcessor processor = new SubscriptionMessageProcessor();
        consumer = new SubscriptionEventConsumer(props,
            new RabbitMqConnectionManager(props), processor);
        consumer.start();
        log.info("Subscription consumer started");
    }

    @PreDestroy
    public void stop() {
        if (consumer != null) {
            log.info("Stopping subscription consumer");
            consumer.stop();
        }
    }
}
