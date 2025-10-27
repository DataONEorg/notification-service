package org.dataone.notifications.api.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionResourceTypeTest {

    @Test
    void fromString() {
        assertEquals(SubscriptionResourceType.datasetChanges, SubscriptionResourceType.fromString("datasetChanges"));
        assertEquals(SubscriptionResourceType.datasetChanges, SubscriptionResourceType.fromString("dataset-changes"));
        assertEquals(SubscriptionResourceType.datasetChanges, SubscriptionResourceType.fromString("DATASETCHANGES"));
        assertEquals(SubscriptionResourceType.citations, SubscriptionResourceType.fromString("citations"));
        assertEquals(SubscriptionResourceType.citations, SubscriptionResourceType.fromString("cit-ations"));
        assertEquals(SubscriptionResourceType.citations, SubscriptionResourceType.fromString("citATIONS"));

        assertThrows(
            IllegalArgumentException.class, () -> SubscriptionResourceType.fromString("not-a-real-resource"));
    }
}
