package org.dataone.notifications.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.dataone.notifications.resource.SubscriptionResourceType;

/**
 * This class represents the structure of a notification event message of the form:
 *
 * {
 *   "nsEvent": {
 *     "subscriberId": "...",
 *     "subscribedPid": "...",
 *     "newPid": "...",
 *     "SubscriptionResourceType": "...",
 *   }
 * }
 *
 * The class is designed to be used with JSON serialization and deserialization, ignoring any
 * unknown properties that may be present in the JSON data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NsEventMessage(NsEvent nsEvent) {

    /**
     * Update the message with the given subscriber id
     * @param subscriberId the subscriber id in the new message
     * @return a new message with the given subscriber id
     */
    public NsEventMessage withSubscriberId(String subscriberId) {
        NsEvent event = nsEvent;
        NsEvent updatedEvent = new NsEvent(
            subscriberId,
            event.subscribedPid(),
            event.newPid(),
            event.SubscriptionResourceType()
        );
        return new NsEventMessage(updatedEvent);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NsEvent(
        String subscriberId,
        String subscribedPid,
        String newPid,
        SubscriptionResourceType SubscriptionResourceType) {}
}
