package org.dataone.notifications.event.consumer;

import com.rabbitmq.client.Channel;
import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.event.NsEventMessage.NsEvent;
import org.dataone.notifications.event.RabbitMqConnection;
import org.dataone.notifications.event.RabbitMqProperties;
import org.dataone.notifications.event.notifier.NotificationListener;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class RabbitMqConsumerIT {

    private static final String QUEUE_NAME = "consumer-test-queue";
    private static final BlockingQueue<NsEvent> receivedEvents = new LinkedBlockingQueue<>();

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:latest");

    @Test
    void consumesEventMessageFromQueue() throws Exception {
        receivedEvents.clear();
        RabbitMqProperties props = new RabbitMqProperties(
            rabbit.getHost(), rabbit.getAmqpPort(), rabbit.getAdminUsername(),
            rabbit.getAdminPassword(), "/", QUEUE_NAME, 10);
        RabbitMqConnection connection = new RabbitMqConnection(props);
        NsEventProcessor processor = new NsEventProcessor(
            new String[] {RecordingListener.class.getName()});
        RabbitMqConsumer consumer = new RabbitMqConsumer(QUEUE_NAME, processor, connection);

        NsEvent event = new NsEvent(
            "subscriber-1", "subscribed-pid", "new-pid", SubscriptionResourceType.datasetChanges);
        byte[] body = new ObjectMapper().writeValueAsBytes(new NsEventMessage(event));

        try {
            try (Channel publisher = connection.newChannel()) {
                publisher.confirmSelect();
                publisher.basicPublish("", QUEUE_NAME, null, body);
                publisher.waitForConfirmsOrDie(10_000);
            }

            consumer.start();

            assertEquals(event, receivedEvents.poll(10, TimeUnit.SECONDS),
                "the listener should receive the published event");
        } finally {
            consumer.close();
        }
    }

    public static class RecordingListener implements NotificationListener {
        @Override
        public String handleNotification(NsEvent event) {
            receivedEvents.add(event);
            return event.subscriberId();
        }
    }
}
