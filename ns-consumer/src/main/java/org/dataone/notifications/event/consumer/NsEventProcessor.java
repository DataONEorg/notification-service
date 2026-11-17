package org.dataone.notifications.event.consumer;

import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.event.NsEventMessage.NsEvent;
import org.dataone.notifications.event.notifier.NotificationListener;
import org.dataone.notifications.exception.NotificationDeliveryException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processor invoked after a RabbitMQ message is consumed.
 */
public class NsEventProcessor {
    private static final Logger log = LoggerFactory.getLogger(NsEventProcessor.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<NotificationListener> listeners = new ArrayList<>();

    public NsEventProcessor(String[] listenerClassNames) {

        if (listenerClassNames == null || listenerClassNames.length == 0) {
            throw new IllegalArgumentException(
                "At least one NotificationListener classname is required. Classnames must be "
                    + "passed to the RabbitMqConsumer.main() method as command-line arguments "
                    + "- e.g. org.dataone.notifications.event.notifier.channel.EmailNotifier");
        }
        for (String className : listenerClassNames) {
            try {
                Class<?> listenerClass = Class.forName(className);
                if (NotificationListener.class.isAssignableFrom(listenerClass)) {
                    NotificationListener listener = (NotificationListener) listenerClass.getDeclaredConstructor().newInstance();
                    listeners.add(listener);
                    log.info("NsEventProcessor Registered listener: {}", className);
                } else {
                    log.error("Class {} does not implement NotificationListener interface", className);
                }
            } catch (Exception e) {
                log.error("Failed to instantiate listener class: {}", className, e);
            }
        }
    }

    public void process(String body) {

        NsEvent event = mapper.readValue(body, NsEventMessage.class).nsEvent();
        Map<String, String> responses = new HashMap<>();
        for (NotificationListener listener : listeners) {
            try {
                responses.put(listener.getClass().getName(), listener.handleNotification(event));
            } catch (UnsupportedResourceTypeException e) {
                log.error("NotificationListener {}: UnsupportedResourceTypeException: {}",
                          listener.getClass().getName(), e.getMessage());
            } catch (NotificationDeliveryException e) {
                log.error("NotificationListener {}: Notification delivery failed: {}",
                          listener.getClass().getName(), e.getMessage());
            }
        }
        for (Map.Entry<String, String> entry : responses.entrySet()) {
            log.debug("NsEventProcessor: Notified {} via {}", entry.getValue(), entry.getKey());
        }
    }
}
