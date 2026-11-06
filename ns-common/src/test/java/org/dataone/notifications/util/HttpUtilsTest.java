package org.dataone.notifications.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

/**
 * Tests HttpUtils with a mocked HttpClient. HttpUtils builds its HttpClient and reads the base URL
 * env var once, at class-initialization time, so both are set up in @BeforeAll before HttpUtils is
 * first touched.
 */
class HttpUtilsTest {

    record Result(String nodeId, long timestamp) {}

    private static final String BASE_URL = "http://ns-api.test:8080";
    private static final String JSON = "{\"nodeId\":\"urn:node:KNB\",\"timestamp\":123}";

    private static EnvironmentVariables env;
    private static HttpClient client;

    @SuppressWarnings("unchecked")
    private final HttpResponse<String> response = mock(HttpResponse.class);

    @BeforeAll
    static void initHttpUtilsWithMockClient() throws Exception {
        // trailing slash verifies that it is stripped
        env = new EnvironmentVariables(HttpUtils.BASE_URL_ENV_VAR, BASE_URL + "/");
        env.setup();

        client = mock(HttpClient.class);
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
            httpClient.when(HttpClient::newBuilder).thenReturn(builder);
            Class.forName(HttpUtils.class.getName());   // triggers static init
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        env.teardown();
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
        String json = HttpUtils.doGet("/lastSuccessfulRuntime/urn:node:KNB/DATA");

        assertEquals(JSON, json);
        HttpRequest request = sentRequest();
        assertEquals("GET", request.method());
        assertEquals(BASE_URL + "/lastSuccessfulRuntime/urn:node:KNB/DATA",
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
        assertEquals(new Result("urn:node:KNB", 123L), HttpUtils.doGet("/x", Result.class));
    }

    @Test
    void doGet_non2xx_throwsIOExceptionWithStatusAndBody() {
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn("nope");

        IOException e = assertThrows(IOException.class, () -> HttpUtils.doGet("/x"));

        assertTrue(e.getMessage().contains("404"));
        assertTrue(e.getMessage().contains("nope"));
    }

    @Test
    void doGet_typed_unparseableBody_throwsIOException() {
        when(response.body()).thenReturn("not json");

        assertThrows(IOException.class, () -> HttpUtils.doGet("/x", Result.class));
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
    void doPut_sendsUrlEncodedFormFields() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("timestamp", 1700000000000L);
        fields.put("nodeId", "urn:node:KNB");
        fields.put("note", "a b&c=d");

        String json = HttpUtils.doPut("/lastSuccessfulRuntime", fields);

        assertEquals(JSON, json);
        HttpRequest request = sentRequest();
        assertEquals("PUT", request.method());
        assertEquals(BASE_URL + "/lastSuccessfulRuntime", request.uri().toString());
        assertEquals("application/x-www-form-urlencoded",
                     request.headers().firstValue("Content-Type").orElse(null));
        assertEquals("application/json", request.headers().firstValue("Accept").orElse(null));
        assertEquals("timestamp=1700000000000&nodeId=urn%3Anode%3AKNB&note=a+b%26c%3Dd",
                     bodyOf(request));
    }

    @Test
    void doPut_skipsNullValues() throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("a", "1");
        fields.put("b", null);

        HttpUtils.doPut("/x", fields);

        assertEquals("a=1", bodyOf(sentRequest()));
    }

    @Test
    void doPut_emptyFields_sendsEmptyBody() throws Exception {
        HttpUtils.doPut("/x", Map.of());

        assertEquals("", bodyOf(sentRequest()));
    }

    @Test
    void doPut_non2xx_throwsIOExceptionWithStatusAndBody() {
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("bad timestamp");

        IOException e = assertThrows(IOException.class, () -> HttpUtils.doPut("/x", Map.of()));

        assertTrue(e.getMessage().contains("400"));
        assertTrue(e.getMessage().contains("bad timestamp"));
        assertFalse(e.getMessage().contains("GET"));
    }
}
