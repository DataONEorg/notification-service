package org.dataone.notifications.eventsource.data;

import org.dataone.notifications.exception.IgnoredResourceTypeException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

/**
 * The JUnit tests for the DataSet class
 */
public class DataSetTest {
    private static final String ORE = "http://www.openarchives.org/ore/terms";
    private static final String PORTAL_1_0_0 = "https://purl.dataone.org/portals-1.0.0";
    private static final String PORTAL_1_1_0 = "https://purl.dataone.org/portals-1.1.0";
    private static final List<String> RESOURCE_MAP_LIST = List.of("urn:uuid:abc");

    @Test
    void main_closesDataSetAfterPoll() throws Exception {
        try (MockedConstruction<DataSet> dataSets = mockConstruction(DataSet.class)) {
            DataSet.main(new String[0]);

            DataSet dataSet = dataSets.constructed().getFirst();
            verify(dataSet).poll();
            verify(dataSet).close();
        }
    }

    @Test
    void main_closesDataSetAndPreservesPollFailure() throws Exception {
        IOException failure = new IOException("poll failed");
        IOException closeFailure = new IOException("close failed");
        try (MockedConstruction<DataSet> dataSets = mockConstruction(DataSet.class,
            (dataSet, context) -> {
                doThrow(failure).when(dataSet).poll();
                doThrow(closeFailure).when(dataSet).close();
            })) {
            IOException thrown = assertThrows(IOException.class, () -> DataSet.main(new String[0]));
            assertSame(failure, thrown);
            assertSame(closeFailure, thrown.getSuppressed()[0]);
            verify(dataSets.constructed().getFirst()).close();
        }
    }

    @Test
    void resource_returnsDatasetChanges() {
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
    void metadataPortal_returnsPortalMetaChanges() {
        assertEquals(SubscriptionResourceType.portalMetaChanges,
                     DataSet.getResourceType("METADATA", PORTAL_1_0_0, null));
        assertEquals(SubscriptionResourceType.portalMetaChanges,
                     DataSet.getResourceType("METADATA", PORTAL_1_1_0, null));
    }

    @Test
    void metadataPortalWithResourceMap_returnsPortalMetaChanges() {
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
    void metadataWithNullResourceMap_returnsDatasetChanges() {
        assertEquals(SubscriptionResourceType.datasetChanges,
                     DataSet.getResourceType("METADATA", ORE, null));
    }

    @Test
    void metadataWithMalformedResourceMap_returnsDatasetChanges() {
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
