package org.dataone.notifications.messaging;

import java.util.Objects;
import org.dataone.notifications.api.resource.SubscriptionResourceType;

/**
 * Represents the payload delivered via the RabbitMQ queue.
 */
public record SubscriptionEvent(SubscriptionResourceType resourceType, String pid) {

    public SubscriptionEvent {
        Objects.requireNonNull(resourceType, "resourceType cannot be null");
        if (pid == null || pid.isBlank()) {
            throw new IllegalArgumentException("pid cannot be blank");
        }
    }

    public static SubscriptionEvent from(String resourceTypeRaw, String pid) {
        SubscriptionResourceType resourceType = SubscriptionResourceType.fromString(resourceTypeRaw);
        return new SubscriptionEvent(resourceType, pid);
    }
}
