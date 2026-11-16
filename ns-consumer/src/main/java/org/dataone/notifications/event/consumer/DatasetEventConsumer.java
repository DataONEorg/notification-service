package org.dataone.notifications.event.consumer;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.dataone.notifications.event.RabbitMqConnection;
import org.dataone.notifications.event.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal RabbitMQ queue consumer that runs until the container shuts down.
 */
public class DatasetEventConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatasetEventConsumer.class);

    private final RabbitMqProperties properties;
    // inlined connection management that was previously in RabbitMqConnectionManager
    private RabbitMqConnection connection;
    private Channel channel;
    private final NsEventProcessor processor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DatasetEventConsumer(
        RabbitMqProperties properties,
        NsEventProcessor processor) throws IOException, TimeoutException {
        this.properties = properties;
        this.processor = processor;
        connection = new RabbitMqConnection(properties);
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
        log.info("Starting DatasetEventConsumer for queue {}",
            properties.queueName());
        try {
            final Channel ch = connection.newChannel();
            channel = ch;
            DeliverCallback callback = (consumerTag, delivery) -> handleDelivery(ch, delivery);
            ch.basicConsume(properties.queueName(), false, callback,
                consumerTag -> log.warn("Consumer {} cancelled by broker", consumerTag));
            log.info("Consuming messages from queue {}", properties.queueName());
        } catch (Exception e) {
            running.set(false);
            shutdown();
            throw new IllegalStateException("Failed to start consumer", e);
        }
    }

    private void handleDelivery(Channel channel, Delivery delivery) throws IOException {
        long tag = delivery.getEnvelope().getDeliveryTag();
        try {
            processor.process(delivery);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Failed to process message. Failed messages are not re-queued. ", e);
            // Disabling the requeue here to avoid bad messages holding up the queue forever:
            //     channel.basicNack(tag, false, true);    // (deliveryTag, multiple?, requeue?)
            // TODO: could eventually add a dead letter queue? Or some other type of operator
            //  alerting. For now, not priority, since this is not mission critical.
            channel.basicAck(tag, false);
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        close();
    }

    @Override
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

    /**
     * Quick check whether an active connection exists.
     */
    public synchronized boolean isConnected() {
        boolean isOpen = connection.isConnected();
        log.trace("connection={}, connection.isOpen={}", connection, isOpen);
        return isOpen;
    }
}
