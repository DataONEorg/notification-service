package org.dataone.notifications.event.consumer;

import org.dataone.notifications.NsConfig;
import org.dataone.notifications.event.RabbitMqConnection;
import org.dataone.notifications.event.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;


/**
 * Entry point that starts the consumer and a file-based health probe.
 */
public class RabbitMqConsumerMain {
    private static final Logger log = LoggerFactory.getLogger(RabbitMqConsumerMain.class);

    private static DatasetEventConsumer consumer;


    static void main(String[] args) throws IOException, TimeoutException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, stopping consumer");
            RabbitMqConnection.stopHealthProbe();
            log.info("Stopped Health Probe");
            if (consumer != null) {
                consumer.stop();
            }
        }));

        // read configuration and create runtime objects
        final RabbitMqProperties props = RabbitMqProperties.from(NsConfig.getConfig());
        final NsEventProcessor processor = new NsEventProcessor();
        consumer = new DatasetEventConsumer(props, processor);

        try {
            consumer.start();
            log.info("Consumer started");
            RabbitMqConnection.startHealthProbe();
        } catch (Exception e) {
            log.error("Failed to start RabbitMQ consumer. Stopping health probe.", e);
            RabbitMqConnection.stopHealthProbe();
            if (consumer != null) {
                consumer.stop();
            }
            System.exit(1);
        }
        log.info(
            "RabbitMQ consumer main thread exiting, consumer and health probe should keep running");
    }

}
