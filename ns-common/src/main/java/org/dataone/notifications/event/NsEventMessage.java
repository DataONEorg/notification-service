package org.dataone.notifications.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.dataone.notifications.resource.SubscriptionResourceType;

/**
 * This class represents the structure of a notification event message.
 * The class is designed to be used with JSON serialization and deserialization, ignoring any
 * unknown properties that may be present in the JSON data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NsEventMessage(NsEvent nsEvent) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NsEvent(
        String subscriberId,
        String subscribedPid,
        String newPid,
        SubscriptionResourceType SubscriptionResourceType) {}
}
