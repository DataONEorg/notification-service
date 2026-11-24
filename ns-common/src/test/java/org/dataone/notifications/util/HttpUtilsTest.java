package org.dataone.notifications.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

import org.dataone.notifications.NsConfig;
import org.dataone.notifications.event.LastModificationTimes;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests HttpUtils with a mocked HttpClient
 */
class HttpUtilsTest {

    private static final String BASE_URL = NsConfig.getConfig().getString("ns.api.privateBaseUrl");
    private static final String JSON = "{\"nodeId\":\"urn:node:KNB\",\"timestamp\":123}";

    private static HttpClient client;

    @SuppressWarnings("unchecked")
    private final HttpResponse<String> response = mock(HttpResponse.class);

    @BeforeAll
    static void initHttpUtilsWithMockClient() throws Exception {
        client = mock(HttpClient.class);
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            httpClient.when(HttpClient::newBuilder).thenReturn(builder);
            Class.forName(HttpUtils.class.getName());   // triggers static init
        }
    }

    @BeforeEach
    void stubResponse() throws Exception {
        reset(client);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(JSON);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response);
    }

    private HttpRequest sentRequest() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        return captor.getValue();
    }

    private static String bodyOf(HttpRequest request) {
        StringBuilder sb = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                sb.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        return sb.toString();
    }

    @Test
    void doGet_returnsBodyAndSendsExpectedRequest() throws Exception {
        String json = HttpUtils.doGet("/lastModificationTime/urn:node:KNB/DATA");

        assertEquals(JSON, json);
        HttpRequest request = sentRequest();
        assertEquals("GET", request.method());
        assertEquals(BASE_URL + "/lastModificationTime/urn:node:KNB/DATA",
                     request.uri().toString());
        assertEquals("application/json", request.headers().firstValue("Accept").orElse(null));
    }

    @Test
    void doGet_uriWithoutLeadingSlash_isNormalized() throws Exception {
        HttpUtils.doGet("some/path");

        assertEquals(BASE_URL + "/some/path", sentRequest().uri().toString());
    }

    @Test
    void doGet_typed_deserializesResponse() throws IOException {
        when(response.body()).thenReturn(
            "{\"runtimes\":[{\"nodeId\":\"urn:node:KNB\",\"timestamp\":123}]}");

        assertEquals(
            new LastModificationTimes(
                List.of(new LastModificationTimes.Entry(123L, "urn:node:KNB"))),
            HttpUtils.doGet("/x", LastModificationTimes.class));
    }

    @Test
    void doGet_404_returnsNull() throws IOException {
        when(response.statusCode()).thenReturn(404);

        assertNull(HttpUtils.doGet("/x"), "Expected null to be returned");
    }

    @Test
    void doGet_non2xx_throwsIOExceptionWithStatusAndBody() {
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("nope");

        IOException e = assertThrows(IOException.class, () -> HttpUtils.doGet("/x"));

        assertTrue(e.getMessage().contains("500"));
        assertTrue(e.getMessage().contains("nope"));
    }

    @Test
    void doGet_typed_unparseableBody_throwsIOException() {
        when(response.body()).thenReturn("not json");

        assertThrows(IOException.class, () -> HttpUtils.doGet("/x", LastModificationTimes.class));
    }

    @Test
    void doGet_clientIOException_propagates() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("connection refused"));

        IOException e = assertThrows(IOException.class, () -> HttpUtils.doGet("/x"));

        assertEquals("connection refused", e.getMessage());
    }

    @Test
    void doGet_interrupted_throwsIOExceptionAndRestoresInterruptFlag() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new InterruptedException());

        try {
            assertThrows(IOException.class, () -> HttpUtils.doGet("/x"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();   // clear the flag so it doesn't leak into other tests
        }
    }

    @Test
    void getSubscriptionSubjects_returnsSubjectList() throws Exception {
        when(response.body()).thenReturn("[\"http://orcid.org/0000-0001-2345-6789\",\"dn:uid=alice,o=org\"]");

        List<String> subjects = HttpUtils.getSubscriptionSubjects(
            "urn:uuid:1234", SubscriptionResourceType.datasetChanges);

        assertEquals(List.of("http://orcid.org/0000-0001-2345-6789", "dn:uid=alice,o=org"),
                     subjects);
        HttpRequest request = sentRequest();
        assertEquals("GET", request.method());
        assertEquals(BASE_URL + "/subscriptions/datasetChanges/urn%3Auuid%3A1234",
                     request.uri().toString());
        assertEquals("application/json", request.headers().firstValue("Accept").orElse(null));
    }

    @Test
    void getSubscriptionSubjects_encodesPidWithSpaces() throws Exception {
        when(response.body()).thenReturn("[]");

        HttpUtils.getSubscriptionSubjects(
            "pid with spaces", SubscriptionResourceType.datasetChanges);

        assertEquals(BASE_URL + "/subscriptions/datasetChanges/pid%20with%20spaces",
                     sentRequest().uri().toString());
    }

    @Test
    void getSubscriptionSubjects_noSubscriptions_returnsEmptyList() throws Exception {
        when(response.body()).thenReturn("[]");

        assertEquals(List.of(), HttpUtils.getSubscriptionSubjects(
            "urn:uuid:nope", SubscriptionResourceType.datasetChanges));
    }

    @Test
    void getSubscriptionSubjects_non2xx_throwsIOExceptionWithStatus() {
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("db down");

        IOException e = assertThrows(IOException.class,
            () -> HttpUtils.getSubscriptionSubjects(
                "urn:uuid:1234", SubscriptionResourceType.datasetChanges));

        assertTrue(e.getMessage().contains("500"));
        assertTrue(e.getMessage().contains("db down"));
    }

    @Test
    void getSubscriptionSubjects_unparseableBody_throwsIOException() {
        when(response.body()).thenReturn("{\"not\":\"a-list\"}");

        assertThrows(IOException.class,
            () -> HttpUtils.getSubscriptionSubjects(
                "urn:uuid:1234", SubscriptionResourceType.datasetChanges));
    }

    @Test
    void doPut_sendsJsonBodyForLastModificationTimeEndpoint() throws Exception {
        String json = HttpUtils.doPut("/lastModificationTime/datasetChanges", List.of(
            new LastModificationTimes.Entry(1700000000000L, "urn:node:KNB"),
            new LastModificationTimes.Entry(1700000060000L, "urn:node:ARCTIC")));

        assertEquals(JSON, json);
        HttpRequest request = sentRequest();
        assertEquals("PUT", request.method());
        assertEquals(BASE_URL + "/lastModificationTime/datasetChanges", request.uri().toString());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(null));
        assertEquals("application/json", request.headers().firstValue("Accept").orElse(null));
        JsonNode sent = new ObjectMapper().readTree(bodyOf(request));
        assertEquals(2, sent.get("runtimes").size());
        assertEquals(1700000000000L, sent.get("runtimes").get(0).get("timestamp").asLong());
        assertTrue(sent.get("runtimes").get(0).get("timestamp").isIntegralNumber());
        assertEquals("urn:node:KNB", sent.get("runtimes").get(0).get("nodeId").asString());
        assertEquals(1700000060000L, sent.get("runtimes").get(1).get("timestamp").asLong());
        assertEquals("urn:node:ARCTIC", sent.get("runtimes").get(1).get("nodeId").asString());
    }

    @Test
    void doPut_emptyEntries_sendsEmptyEntriesArray() throws Exception {
        HttpUtils.doPut("/x", List.of());

        assertEquals("{\"runtimes\":[]}", bodyOf(sentRequest()));
    }

    @Test
    void doPut_uriWithoutLeadingSlash_isNormalized() throws Exception {
        HttpUtils.doPut("some/path", List.of());

        assertEquals(BASE_URL + "/some/path", sentRequest().uri().toString());
    }

    @Test
    void doPut_non2xx_throwsIOExceptionWithStatusAndBody() {
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("bad timestamp");

        IOException e = assertThrows(IOException.class, () -> HttpUtils.doPut("/x", List.of()));

        assertTrue(e.getMessage().contains("400"));
        assertTrue(e.getMessage().contains("bad timestamp"));
        assertFalse(e.getMessage().contains("GET"));
    }

    @Test
    void updateSubscriptionPid_returnsUpdatedCount() throws Exception {
        when(response.body()).thenReturn("{\"updated\":2}");

        int updated = HttpUtils.updateSubscriptionPid(
            "urn:uuid:old", "urn:uuid:new", "http://orcid.org/0000-0001-2345-6789");

        assertEquals(2, updated);
        HttpRequest request = sentRequest();
        assertEquals("PUT", request.method());
        assertEquals(BASE_URL + "/subscriptions/urn%3Auuid%3Aold/urn%3Auuid%3Anew/"
                         + "http%3A%2F%2Forcid.org%2F0000-0001-2345-6789",
                     request.uri().toString());
    }

    @Test
    void updateSubscriptionPid_encodesSpaces() throws Exception {
        when(response.body()).thenReturn("{\"updated\":1}");

        HttpUtils.updateSubscriptionPid("old pid", "new pid", "user id");

        assertEquals(BASE_URL + "/subscriptions/old%20pid/new%20pid/user%20id",
                     sentRequest().uri().toString());
    }

    @Test
    void updateSubscriptionPid_non2xx_throwsIOExceptionWithStatus() {
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn("not found");

        IOException e = assertThrows(IOException.class,
            () -> HttpUtils.updateSubscriptionPid("urn:uuid:old", "urn:uuid:new", "subject"));

        assertTrue(e.getMessage().contains("404"));
        assertTrue(e.getMessage().contains("not found"));
    }

    @Test
    void updateSubscriptionPid_unparseableBody_throwsIOException() {
        when(response.body()).thenReturn("not json");

        assertThrows(IOException.class,
            () -> HttpUtils.updateSubscriptionPid("urn:uuid:old", "urn:uuid:new", "subject"));
    }
}
