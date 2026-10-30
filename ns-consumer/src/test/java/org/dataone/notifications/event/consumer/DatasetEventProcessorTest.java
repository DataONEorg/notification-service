package org.dataone.notifications.event.consumer;

import com.rabbitmq.client.Delivery;
import org.dataone.notifications.api.resource.SubscriptionResourceType;
import org.dataone.notifications.event.NsEventMessage.NsEvent;
import org.dataone.notifications.exception.IgnoredResourceTypeException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class DatasetEventProcessorTest {

    private static final String ORE = "http://www.openarchives.org/ore/terms";
    private static final String PORTAL_1_0_0 = "https://purl.dataone.org/portals-1.0.0";
    private static final String PORTAL_1_1_0 = "https://purl.dataone.org/portals-1.1.0";
    private static final List<String> RESOURCE_MAP_LIST = List.of("urn:uuid:abc");

    private static final String PID = "resource_map_urn:uuid:71f96fe5-ed91-4923-89aa-0ab5171d5a01";

    @SystemStub
    private EnvironmentVariables env;

    private final DatasetEventProcessor processor = new DatasetEventProcessor();

    @BeforeEach
    void setMessagesServiceEnv() {
        env.set("NS_MESSAGES_HOST", "msg-host");
        env.set("NS_MESSAGES_PORT", "8080");
    }

    @Test
    void resource_returnsDatasetChanges() throws Exception {
        assertEquals(SubscriptionResourceType.datasetChanges,
                     processor.getResourceType(event("RESOURCE", ORE, RESOURCE_MAP_LIST)));
    }

    @Test
    void data_isIgnored() {
        assertThrows(IgnoredResourceTypeException.class,
                     () -> processor.getResourceType(event("DATA", "text/csv", null)));
        assertThrows(IgnoredResourceTypeException.class,
                     () -> processor.getResourceType(event("DATA", "text/csv", RESOURCE_MAP_LIST)));
    }

    @Test
    void metadataPortal_returnsPortalMetaChanges() throws Exception {
        assertEquals(SubscriptionResourceType.portalMetaChanges,
                     processor.getResourceType(event("METADATA", PORTAL_1_0_0, null)));
        assertEquals(SubscriptionResourceType.portalMetaChanges,
                     processor.getResourceType(event("METADATA", PORTAL_1_1_0, null)));
    }

    @Test
    void metadataPortalWithResourceMap_returnsPortalMetaChanges() throws Exception {
        assertEquals(SubscriptionResourceType.portalMetaChanges, processor.getResourceType(
            event("METADATA", PORTAL_1_0_0, RESOURCE_MAP_LIST)));
        assertEquals(SubscriptionResourceType.portalMetaChanges, processor.getResourceType(
            event("METADATA", PORTAL_1_1_0, RESOURCE_MAP_LIST)));
    }

    @Test
    void metadataWithResourceMap_isIgnored() {
        NsEvent event = event("METADATA", ORE, RESOURCE_MAP_LIST);
        assertThrows(IgnoredResourceTypeException.class, () -> processor.getResourceType(event));
    }

    @Test
    void metadataWithNullResourceMap_returnsDatasetChanges() throws Exception {
        assertEquals(SubscriptionResourceType.datasetChanges,
                     processor.getResourceType(event("METADATA", ORE, null)));
    }

    @Test
    void metadataWithMalformedResourceMap_returnsDatasetChanges() throws Exception {
        assertEquals(SubscriptionResourceType.datasetChanges,
                     processor.getResourceType(event("METADATA", ORE, List.of())));
        assertEquals(SubscriptionResourceType.datasetChanges,
                     processor.getResourceType(event("METADATA", ORE, List.of(" "))));
    }

    @Test
    void unknownFormatType_isUnsupported() {
        NsEvent event = event("BOGUS", ORE, null);
        assertThrows(UnsupportedResourceTypeException.class,
                     () -> processor.getResourceType(event));
    }

    @Test
    void missingFormatType_isUnsupported() {
        NsEvent event = event(null, ORE, null);
        assertThrows(UnsupportedResourceTypeException.class,
                     () -> processor.getResourceType(event));
    }

    @Test
    void missingFormatId_isUnsupported() {
        NsEvent event = event("METADATA", null, null);
        assertThrows(UnsupportedResourceTypeException.class,
                     () -> processor.getResourceType(event));
    }

    private static NsEvent event(String formatType, String formatId, List<String> resourceMap) {
        NsEvent event = mock(NsEvent.class);
        lenient().when(event.formatType()).thenReturn(formatType);
        lenient().when(event.formatId()).thenReturn(formatId);
        lenient().when(event.resourceMap()).thenReturn(resourceMap);
        return event;
    }

    @Test
    void process_acceptedEvent_postsToMessagesService() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            httpClient.when(HttpClient::newHttpClient).thenReturn(client);

            processor.process(delivery(json("RESOURCE")));

            ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
            verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
            assertEquals("POST", request.getValue().method());
            assertEquals("http://msg-host:8080/notifications/v1/private/eventhandler/send"
                             + "?resource=datasetChanges&pid=" + PID,
                         request.getValue().uri().toString());
        }
    }

    @Test
    void process_ignoredEvent_sendsNothing() throws Exception {
        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            processor.process(delivery(json("DATA")));

            httpClient.verify(HttpClient::newHttpClient, never());
        }
    }

    @Test
    void process_httpFailure_isSwallowed() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("connection refused"));
        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            httpClient.when(HttpClient::newHttpClient).thenReturn(client);

            processor.process(delivery(json("RESOURCE")));

            verify(client).send(any(), any(HttpResponse.BodyHandler.class));
        }
    }

    @Test
    void process_malformedBody_throwsIOException() {
        assertThrows(IOException.class, () -> processor.process(delivery("not json")));
    }

    private static String json(String formatType) {
        return """
            {
              "nsEvent": {
                "_version_":1879779856146759680,
                "id":"resource_map_urn:uuid:71f96fe5-ed91-4923-89aa-0ab5171d5a01",
                "identifier":"resource_map_urn:uuid:71f96fe5-ed91-4923-89aa-0ab5171d5a01",
                "sku":"resource_map_urn:uuid:71f96fe5-ed91-4923-89aa-0ab5171d5a01",
                "fileName":"resource_map_urn_uuid_8a592fd1_88f9_42fc_8a25_a0439af2a042.rdf.xml",
                "formatId":"http://www.openarchives.org/ore/terms",
                "formatType":"FORMAT_TYPE",
                "resourceMap":["urn:uuid:71f96fe5-ed91-4923-89aa-0ab5171d5a01"],
                "size":4388,
                "checksum":"4aaadbed267184a6d798b53aabaa39ee",
                "submitter":"http://orcid.org/0000-0002-1615-3963",
                "checksumAlgorithm":"MD5",
                "rightsHolder":"http://orcid.org/0000-0002-1615-3963",
                "replicationAllowed":false,
                "obsoletes":"resource_map_urn:uuid:f6b48ea3-b6a6-45a0-866f-25c36b283e79",
                "archived":false,
                "dateUploaded":"2026-10-22T19:36:07.717Z",
                "updateDate":"2026-10-22T19:36:07.717Z",
                "dateModified":"2026-10-22T19:36:07.858Z",
                "datasource":"urn:node:mnTestKNB",
                "authoritativeMN":"urn:node:mnTestKNB",
                "readPermission":["public",
                  "CN=arctic-data-admins,DC=dataone,DC=org",
                  "http://orcid.org/0000-0003-0391-541X"],
                "writePermission":["CN=arctic-data-admins,DC=dataone,DC=org",
                  "http://orcid.org/0000-0003-0391-541X"],
                "changePermission":["CN=arctic-data-admins,DC=dataone,DC=org"],
                "isPublic":true,
                "dataUrl":"https://cn.dataone.org/cn/v2/resolve/resource_map_urn%3Auuid%3A71f96fe5-ed91-4923-89aa-0ab5171d5a01",
                "prov_instanceOfClass":["http://www.openarchives.org/ore/terms/ResourceMap"],
                "serviceCoupling":"false",
                "isService":false
              }
            }
            """.replace("FORMAT_TYPE", formatType);
    }

    private static Delivery delivery(String body) {
        return new Delivery(null, null, body.getBytes(StandardCharsets.UTF_8));
    }
}
