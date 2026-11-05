package org.dataone.notifications.event.consumer;

import com.rabbitmq.client.Delivery;
import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.event.NsEventMessage.NsEvent;
import org.dataone.notifications.exception.IgnoredResourceTypeException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Processor invoked after a RabbitMQ message is consumed.
 */
public class NsEventProcessor {
    private static final Logger log = LoggerFactory.getLogger(NsEventProcessor.class);
    private final ObjectMapper mapper = new ObjectMapper();

    public void process(Delivery delivery) {

        String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
        log.trace("Message body: {}", body);
        NsEvent event;
        try {
            event = mapper.readValue(body, NsEventMessage.class).nsEvent();
            log.debug("Deserialized NsEvent {}", event);

        } catch (IgnoredResourceTypeException | UnsupportedResourceTypeException e) {
            log.debug("Ignoring message because: {}. Body is: {}", e.getMessage(), body);
            return;
        }
        log.debug("Processing subscription event: {}", event);

        // TODO: Implement the actual processing logic for the event here.
    }
}
