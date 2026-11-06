package org.dataone.notifications.eventsource.data;

import org.dataone.notifications.exception.IgnoredResourceTypeException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Junit tests for the DataSet class
 */
public class DataSetTest {
    private static final String ORE = "http://www.openarchives.org/ore/terms";
    private static final String PORTAL_1_0_0 = "https://purl.dataone.org/portals-1.0.0";
    private static final String PORTAL_1_1_0 = "https://purl.dataone.org/portals-1.1.0";
    private static final List<String> RESOURCE_MAP_LIST = List.of("urn:uuid:abc");


    @Test
    void resource_returnsDatasetChanges() throws Exception {
        assertEquals(
            SubscriptionResourceType.datasetChanges,
            DataSet.getResourceType("RESOURCE", ORE, RESOURCE_MAP_LIST));
    }

    @Test
    void data_isIgnored() {
        assertThrows(
            IgnoredResourceTypeException.class,
            () -> DataSet.getResourceType("DATA", "text/csv", null));
        assertThrows(IgnoredResourceTypeException.class,
                     () -> DataSet.getResourceType("DATA", "text/csv", RESOURCE_MAP_LIST));
    }

    @Test
    void metadataPortal_returnsPortalMetaChanges() throws Exception {
        assertEquals(SubscriptionResourceType.portalMetaChanges,
                     DataSet.getResourceType("METADATA", PORTAL_1_0_0, null));
        assertEquals(SubscriptionResourceType.portalMetaChanges,
                     DataSet.getResourceType("METADATA", PORTAL_1_1_0, null));
    }

    @Test
    void metadataPortalWithResourceMap_returnsPortalMetaChanges() throws Exception {
        assertEquals(SubscriptionResourceType.portalMetaChanges, DataSet.getResourceType(
            "METADATA", PORTAL_1_0_0, RESOURCE_MAP_LIST));
        assertEquals(SubscriptionResourceType.portalMetaChanges, DataSet.getResourceType(
            "METADATA", PORTAL_1_1_0, RESOURCE_MAP_LIST));
    }

    @Test
    void metadataWithResourceMap_isIgnored() {
        assertThrows(
            IgnoredResourceTypeException.class,
            () -> DataSet.getResourceType("METADATA", ORE, RESOURCE_MAP_LIST));
    }

    @Test
    void metadataWithNullResourceMap_returnsDatasetChanges() throws Exception {
        assertEquals(SubscriptionResourceType.datasetChanges,
                     DataSet.getResourceType("METADATA", ORE, null));
    }

    @Test
    void metadataWithMalformedResourceMap_returnsDatasetChanges() throws Exception {
        assertEquals(SubscriptionResourceType.datasetChanges,
                     DataSet.getResourceType("METADATA", ORE, List.of()));
        assertEquals(SubscriptionResourceType.datasetChanges,
                     DataSet.getResourceType("METADATA", ORE, List.of(" ")));
    }

    @Test
    void unknownFormatType_isUnsupported() {
        assertThrows(
            UnsupportedResourceTypeException.class,
            () -> DataSet.getResourceType("BOGUS", ORE, null));
    }

    @Test
    void missingFormatType_isUnsupported() {
        assertThrows(IllegalArgumentException.class,
                     () -> DataSet.getResourceType(null, ORE, null));
    }

    @Test
    void missingFormatId_isUnsupported() {
        assertThrows(IllegalArgumentException.class,
                     () -> DataSet.getResourceType("METADATA", null, null));
    }
}
