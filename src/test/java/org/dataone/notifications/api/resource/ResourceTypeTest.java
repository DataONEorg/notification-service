package org.dataone.notifications.api.resource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceTypeTest {

    @Test
    void fromString() {
        assertEquals(ResourceType.datasetChanges, ResourceType.fromString("datasetChanges"));
        assertEquals(ResourceType.datasetChanges, ResourceType.fromString("dataset-changes"));
        assertEquals(ResourceType.datasetChanges, ResourceType.fromString("DATASETCHANGES"));
        assertEquals(ResourceType.citations, ResourceType.fromString("citations"));
        assertEquals(ResourceType.citations, ResourceType.fromString("cit-ations"));
        assertEquals(ResourceType.citations, ResourceType.fromString("citATIONS"));

        assertThrows(
            IllegalArgumentException.class, () -> ResourceType.fromString("not-a-real-resource"));
    }
}
