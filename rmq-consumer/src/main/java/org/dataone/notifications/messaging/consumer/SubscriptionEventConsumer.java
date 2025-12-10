package org.dataone.notifications.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dataone.notifications.messaging.SubscriptionEvent;
import org.dataone.notifications.messaging.config.RabbitMqConnectionManager;
import org.dataone.notifications.messaging.config.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal RabbitMQ queue consumer that runs until the container shuts down.
 */
public class SubscriptionEventConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventConsumer.class);

    private final RabbitMqProperties properties;
    private final RabbitMqConnectionManager connectionManager;
    private final SubscriptionMessageProcessor processor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "subscription-event-consumer");
        t.setDaemon(true);
        return t;
    });

    public SubscriptionEventConsumer(
        RabbitMqProperties properties,
        RabbitMqConnectionManager connectionManager,
        SubscriptionMessageProcessor processor) {
        this.properties = properties;
        this.connectionManager = connectionManager;
        this.processor = processor;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor.submit(this::consumeLoop);
    }

    private void consumeLoop() {
        log.info("Starting RabbitMQ consumer loop for queue {}", properties.queueName());
        while (running.get()) {
            try (Channel channel = connectionManager.newChannel()) {
                DeliverCallback callback = (consumerTag, delivery) -> handleDelivery(channel, delivery);
                channel.basicConsume(properties.queueName(), false, callback, consumerTag -> {});
                while (running.get() && channel.isOpen()) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Consumer thread interrupted");
                return;
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }
                log.error("Queue consumption failed, retrying in 5s", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handleDelivery(Channel channel, Delivery delivery) throws IOException {
        long tag = delivery.getEnvelope().getDeliveryTag();
        try {
            SubscriptionEvent event = deserialize(delivery);
            processor.process(event);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Failed to process message, requeue={}", properties.requeueOnError(), e);
            boolean requeue = properties.requeueOnError();
            channel.basicNack(tag, false, requeue);
        }
    }

    private SubscriptionEvent deserialize(Delivery delivery) throws IOException {
        String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
        JsonNode node = mapper.readTree(body);
        String resourceType = getRequiredText(node, "resourceType");
        String pid = getRequiredText(node, "pid");
        return SubscriptionEvent.from(resourceType, pid);
    }

    private String getRequiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing value for field: " + field);
        }
        return value.asText();
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        executor.shutdownNow();
        close();
    }

    @Override
    public void close() {
        connectionManager.close();
    }
}
