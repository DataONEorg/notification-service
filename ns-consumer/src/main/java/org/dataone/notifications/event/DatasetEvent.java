package org.dataone.notifications.event;

import java.util.Objects;
import org.dataone.notifications.api.resource.SubscriptionResourceType;

/**
 * Represents the payload delivered via the RabbitMQ queue.
 */
public record DatasetEvent(SubscriptionResourceType resourceType, String pid) {

    public DatasetEvent {
        Objects.requireNonNull(resourceType, "resourceType cannot be null");
        if (pid == null || pid.isBlank()) {
            throw new IllegalArgumentException("pid cannot be blank");
        }
    }

    public static DatasetEvent from(String resourceTypeRaw, String pid) {
        SubscriptionResourceType resourceType = SubscriptionResourceType.fromString(resourceTypeRaw);
        return new DatasetEvent(resourceType, pid);
    }
}
