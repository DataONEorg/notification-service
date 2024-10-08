package org.dataone.notifications.storage;

import org.dataone.notifications.api.resource.ResourceType;

import java.util.List;

/**
 * A record containing subscription details, that can be marshalled or unmarshalled (e.g. as json).
 * As stated by JEP 395, Records are meant to be "transparent carriers for immutable data". Default
 * constructor, getters, hashCode/equals and toString() will be generated by the compiler. See
 * https://openjdk.org/jeps/395
 *
 * @param subject      The unique identifier for this user (e.g. an orcid or DN), as stored in the
 *                     account service
 * @param resourceType Denotes the type of resource the user wants to monitor for changes (e.g. a
 *                     dataset, citations, etc). Lowercase enum value
 * @param resourceIds  An array of zero or more {@code pids} of the given {@code resourceType}, to
 *                     which this {@code subject} is subscribed
 */
public record Subscription(String subject, Enum<ResourceType> resourceType,
                           List<String> resourceIds) {
}
