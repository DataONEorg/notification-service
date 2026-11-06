package org.dataone.notifications.event;

import org.dataone.notifications.resource.SubscriptionResourceType;

import java.time.Instant;

public record LastSuccessfulRuntimeResponse(
    Instant timestampEpochMillis,
    String nodeId,
    SubscriptionResourceType resourceType) {
}
