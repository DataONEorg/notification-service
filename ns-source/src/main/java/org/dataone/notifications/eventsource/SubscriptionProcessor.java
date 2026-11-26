package org.dataone.notifications.eventsource;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.dataone.notifications.NsConfig;
import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.event.RabbitMqConnection;
import org.dataone.notifications.event.RabbitMqProperties;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.dataone.notifications.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * The class to get/update subscription information for a pid. Also, it will submit the event
 * information to RabbitMQ.
 */
public class SubscriptionProcessor implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionProcessor.class);
    private static final RabbitMqProperties properties =
        RabbitMqProperties.from(NsConfig.getConfig());
    private static volatile SubscriptionProcessor processor;
    private final RabbitMqConnection rabbitMqConnection;
    private final ObjectMapper objectMapper;
    private Channel channel;

    /**
     * Default constructor
     *
     * @throws IOException      if the rabbitMqConnection connection cannot be established
     * @throws TimeoutException if the rabbitMqConnection connection times out
     */
    private SubscriptionProcessor() throws IOException, TimeoutException {
        rabbitMqConnection = new RabbitMqConnection(properties);
        try {
            channel = rabbitMqConnection.newChannel();
            objectMapper = new ObjectMapper();
        } catch (IOException | TimeoutException | RuntimeException | Error e) {
            try {
                rabbitMqConnection.close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
    }

    /**
     * Get the singleton instance of the class
     *
     * @return an instance of this class
     * @throws IOException      if the rabbitMqConnection connection cannot be established
     * @throws TimeoutException if the rabbitMqConnection connection times out
     */
    public static SubscriptionProcessor getInstance() throws IOException, TimeoutException {
        if (processor == null) {
            synchronized (SubscriptionProcessor.class) {
                if (processor == null) {
                    processor = new SubscriptionProcessor();
                }
            }
        }
        return processor;
    }

    public static synchronized void closeInstance() throws IOException {
        if (processor != null) {
            processor.close();
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (SubscriptionProcessor.class) {
            try {
                rabbitMqConnection.close();
            } finally {
                if (processor == this) {
                    processor = null;
                }
            }
        }
    }

    /**
     * Process the event message
     *
     * @param message the message need to be processed
     */
    public void process(NsEventMessage message) {

        ensureNotNull(message.nsEvent(), "Event");
        SubscriptionResourceType subscriptionType = message.nsEvent().SubscriptionResourceType();
        ensureNotNull(subscriptionType, "subscription type");
        String pid = message.nsEvent().subscribedPid();
        ensureNotNull(pid, "pid");
        String newPid = message.nsEvent().newPid();
        ensureNotNull(newPid, "newPid");

        SubscriptionResourceType type = message.nsEvent().SubscriptionResourceType();
        List<String> subscribers = getSubscribers(pid, type);
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("There are no subscribers for pid {} with type {}", pid, type.name());
            return;
        }
        log.debug(
            "The {} event for pid {} has {} subscribers: {}",
                type, pid, subscribers.size(), subscribers);
        for (String subscriber : subscribers) {
            if (subscriber == null || subscriber.isBlank()) {
                log.warn(
                    "This subscriber id is null or blank for pid {} and type {}", pid, type.name());
            } else {
                message = message.withSubscriberId(subscriber);
                try {
                    submit(message);
                    log.debug(
                        "The {} event for pid {} and subscriber {} was submitted to RabbitMQ.",
                        type, pid, subscriber);
                } catch (Exception e) {
                    log.error(
                        "The {} event for pid {} and subscriber {} cannot be "
                            + "submitted to RabbitMQ since {}", type, pid, subscriber,
                        e.getMessage());
                    continue;
                }
                try {
                    updateSubscriptions(pid, newPid, type);
                    log.debug(
                        "The subscription for {} and {} has been updated with the new pid " + "{}",
                        pid, type.name(), newPid);
                } catch (Exception e) {
                    log.error(
                        "Could not update the subscription for {} and {} with the new pid " + "{}",
                        pid, type.name(), newPid);
                }
            }
        }
    }

    /**
     * Get the all subscriber ids for a given pid for the subscription resource type
     *
     * @param pid  the object id for the subscription
     * @param type the subscription resource type for the object
     * @return the list of subscriber ids; null will return if there are no subscribers
     */
    private List<String> getSubscribers(String pid, SubscriptionResourceType type) {
        try {
            List<String> subjects = HttpUtils.getSubscriptionSubjects(pid, type);
            log.debug(
                "Retrieved the following {} subscribers for pid {} and type {}: {}",
                subjects.size(), pid, type.name(), subjects);
            return subjects.isEmpty() ? null : subjects;
        } catch (IOException e) {
            log.error(
                "Error retrieving subscribers for pid {} and type {}: {}", pid, type,
                e.getMessage());
            return null;
        }
    }

    /**
     * Update the subscription to a new pid from an old one.
     *
     * @param pid    the old subscription object
     * @param newPid the new subscription object
     * @param type   the type of the subscription resource
     */
    private void updateSubscriptions(String pid, String newPid, SubscriptionResourceType type) {
        try {
            List<String> subjects = HttpUtils.getSubscriptionSubjects(pid, type);
            if (subjects == null || subjects.isEmpty()) {
                log.debug("No subscriptions found for pid {} with type {}", pid, type.name());
                return;
            }
            for (String subject : subjects) {
                try {
                    int updated = HttpUtils.updateSubscriptionPid(pid, newPid, subject);
                    log.debug(
                        "Updated {} subscription(s) from pid {} to {} for subject {}",
                        updated, pid, newPid, subject);
                } catch (Exception e) {
                    log.error(
                        "Could not update subscription from pid {} to {} for subject {}: {}",
                        pid, newPid, subject, e.getMessage());
                    throw e;
                }
            }
        } catch (IOException e) {
            log.error(
                "Error updating subscriptions for pid {} and type {}: {}", pid, type,
                e.getMessage());
        }
    }

    private void submit(NsEventMessage message) throws IOException, TimeoutException {
        if (message == null) {
            log.debug("The NsEventMessage object is null and the service will do nothing.");
            return;
        }
        byte[] payload = objectMapper.writeValueAsBytes(message);
        if (channel == null || !channel.isOpen()) {
            try {
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException | TimeoutException e) {
                log.warn("Can't close the channel since {}", e.getMessage());
            }
            channel = rabbitMqConnection.newChannel();
            log.debug("Created a new RabbitMQ connection");
        }
        AMQP.BasicProperties properties =
            new AMQP.BasicProperties.Builder().contentType("application/json")
                .contentEncoding("UTF-8").deliveryMode(2)  // Persistent
                .build();
        channel.basicPublish(
            SubscriptionProcessor.properties.exchangeName(),
            SubscriptionProcessor.properties.routingKey(), properties, payload);
    }

    private void ensureNotNull(Object obj, String objectReadableName)
        throws IllegalArgumentException {
        if (obj == null) {
            throw new IllegalArgumentException(
                "The " + objectReadableName + " in the NsEventMessage object cannot be " + "null.");
        }
    }
}
