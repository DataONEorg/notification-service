package org.dataone.notifications.event.consumer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.dataone.notifications.NsConfig;
import org.dataone.notifications.event.RabbitMqConnection;
import org.dataone.notifications.event.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal RabbitMQ queue consumer that runs until the container shuts down.
 */
public class RabbitMqConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConsumer.class);
    private final String queueName;
    private final NsEventProcessor processor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private RabbitMqConnection connection;
    private Channel channel;

    RabbitMqConsumer(String queueName, NsEventProcessor processor, RabbitMqConnection connection) throws IllegalStateException {

        if (connection == null | processor == null | queueName == null || queueName.isBlank()) {
            throw new IllegalStateException(
                "RabbitMqConsumer connection {" + connection + "}, processor {" + processor
                    + "}, or queue name {" + queueName + "} invalid");
        }
        this.queueName = queueName;
        this.processor = processor;
        this.connection = connection;
    }

    /**
     * Main method to start the RabbitMQ consumer. It initializes the RabbitMQ connection and
     * registers a shutdown hook to stop the consumer and health probe on application shutdown.
     *
     * @param args              Command-line arguments (not used).
     * @throws IOException      If an I/O error occurs during consumer startup.
     * @throws TimeoutException If a timeout occurs while establishing the RabbitMQ connection.
     */
    static void main(String[] args) throws IOException, TimeoutException {

        final RabbitMqProperties props = RabbitMqProperties.from(NsConfig.getConfig());
        final NsEventProcessor processor = new NsEventProcessor(NsConfig.getConfig());
        RabbitMqConnection connection = new RabbitMqConnection(props);
        final String queueName = props.queueName();
        final RabbitMqConsumer consumer = new RabbitMqConsumer(queueName, processor, connection);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received. Stopping RMQ connection health Probe...");
            connection.stopHealthProbe();
            log.info("Stopping RabbitMQ Consumer...");
            consumer.stop();
        }));

        try {
            consumer.start();
            log.info("Consumer started");
            connection.startHealthProbe();
            log.info("Consumer RMQ connection health probe started");
        } catch (Exception e) {
            log.error("Failed to start RabbitMQ consumer. Stopping health probe.", e);
            connection.stopHealthProbe();
            consumer.stop();
            System.exit(1);
        }
        log.info(
            "RabbitMQ consumer main thread exiting; consumer and health probe should keep running");
    }

    /**
     * Registers a single consumer on the queue. Reconnects and consumer re-registration after
     * network failures are handled by the RabbitMQ client's automatic recovery. If the broker is
     * unreachable at startup, this throws so the container can fail and be restarted.
     */
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        log.info("Starting RabbitMqConsumer for queue {}", queueName);
        try {
            final Channel ch = connection.newChannel();
            channel = ch;
            DeliverCallback callback = (_, delivery) -> handleDelivery(ch, delivery);
            ch.basicConsume(
                queueName, false, callback,
                consumerTag -> log.warn("Consumer {} cancelled by broker", consumerTag));
            log.info("Consuming messages from queue {}", queueName);
        } catch (Exception e) {
            running.set(false);
            shutdown();
            throw new IllegalStateException("Failed to start consumer", e);
        }
    }

    private void handleDelivery(Channel channel, Delivery delivery) throws IOException {

        long tag = delivery.getEnvelope().getDeliveryTag();
        String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
        log.trace("Received message body: {}", body);
        try {
            processor.process(body);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Failed to process message with tag {}. Failed messages are not re-queued."
                + " body was: {}", tag, body, e);
            // Disabling the requeue to avoid bad messages holding up the queue for ever:
            //   channel.basicNack(tag, false, true);    // basicNack(tag, multiple?, requeue?)
            // TODO: could eventually add a dead letter queue? Or some other type of operator
            //  alerting. Not a priority for now, since this is not mission critical.
            channel.basicAck(tag, false);
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        close();
    }

    public void close() {
        shutdown();
    }

    public synchronized void shutdown() {
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (Exception e) {
            log.warn("Error closing RabbitMQ channel", e);
        } finally {
            channel = null;
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception e) {
            log.warn("Error closing RabbitMQ connection", e);
        } finally {
            connection = null;
        }
    }
}
