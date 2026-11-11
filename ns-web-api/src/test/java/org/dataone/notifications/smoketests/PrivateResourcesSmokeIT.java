package org.dataone.notifications.smoketests;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NOTE EXPECTED TO FAIL (BY DESIGN) IF USED AGAINST PUBLIC INGRESS! THESE ENDPOINTS MUST NEVER BE
 * EXPOSED TO THE PUBLIC INTERNET.
 *
 * Smoke tests for the private (cluster-only) endpoints.
 * Requires BASE_URL, but no TOKEN. Usage:
 *
 * $ export BASE_URL=http://localhost:8080/notifications/v1
 * $ mvn clean -PsmokeTest -DBASE_URL="$BASE_URL" -DTOKEN="$TOKEN" verify
 */
class PrivateResourcesSmokeIT {

    private static final String URI_PREFIX = "notifications/v1";
    private static final String PRIVATE_PATH = "private";
    private static final String LAST_RUNTIME_PATH = "lastSuccessfulRuntime";
    // Constant (resourceType, nodeId) keys: the first run inserts these rows, and every later run
    // only updates their timestamps, so the live DB never grows beyond these rows.
    private static final String TEST_NODE_ID = "urn:node:SMOKE_TEST";
    private static final String TEST_NODE_ID_2 = "urn:node:SMOKE_TEST_2";
    private static final String TYPE = SubscriptionResourceType.testType.name();
    // Never written by any test, so it has no rows
    private static final SubscriptionResourceType EMPTY_TYPE = SubscriptionResourceType.testNoType;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private String baseUrl;
    private Client client;

    @BeforeEach
    void setUp() {
        baseUrl = System.getProperty("BASE_URL", System.getenv("BASE_URL"));
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("BASE_URL is required for these smoke tests");
        }
        client = ClientBuilder.newClient();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void setLastRuntime_multipleEntries_returnsNoContent() {
        long now = Instant.now().toEpochMilli();
        log.debug("PUT lastSuccessfulRuntime, timestamps={}", now);

        try (Response r = put(TYPE,
                              body(entry(now - 60_000, TEST_NODE_ID),
                                   entry(now - 90_000, TEST_NODE_ID_2)))) {
            assertEquals(204, r.getStatus(), "unexpected status");
        }
    }

    @Test
    void setLastRuntime_timestampInFuture_returnsBadRequest() {
        long now = Instant.now().toEpochMilli();
        try (Response r = put(TYPE,
                              body(entry(now, TEST_NODE_ID),
                                   entry(now + 3_600_000, TEST_NODE_ID_2)))) {
            assertEquals(400, r.getStatus(), "future timestamp should be rejected");
        }
    }

    @Test
    void setLastRuntime_timestampTooOld_returnsBadRequest() {
        long now = Instant.now().toEpochMilli();
        long eightDaysMs = 8L * 24 * 60 * 60 * 1000;
        try (Response r = put(TYPE,
                              body(entry(now, TEST_NODE_ID),
                                   entry(now - eightDaysMs, TEST_NODE_ID_2)))) {
            assertEquals(400, r.getStatus(), "timestamp older than 7 days should be rejected");
        }
    }

    @Test
    void setLastRuntime_missingNodeId_returnsBadRequest() {
        ObjectNode noNodeId = MAPPER.createObjectNode().put("timestamp", Instant.now().toEpochMilli());
        try (Response r = put(TYPE, body(entry(Instant.now().toEpochMilli(), TEST_NODE_ID),
                                         noNodeId))) {
            assertEquals(400, r.getStatus(), "missing nodeId should be rejected");
        }
    }

    @Test
    void setLastRuntime_blankNodeId_returnsBadRequest() {
        try (Response r = put(TYPE, body(entry(Instant.now().toEpochMilli(), " ")))) {
            assertEquals(400, r.getStatus(), "blank nodeId should be rejected");
        }
    }

    @Test
    void setLastRuntime_missingTimestamp_returnsBadRequest() {
        ObjectNode noTimestamp = MAPPER.createObjectNode().put("nodeId", TEST_NODE_ID);
        try (Response r = put(TYPE, body(noTimestamp))) {
            assertEquals(400, r.getStatus(), "missing timestamp should be rejected");
        }
    }

    @Test
    void setLastRuntime_malformedBody_returnsClientError() {
        try (Response r = put(TYPE, "{\"runtimes\": \"not-a-list\"}")) {
            int status = r.getStatus();
            assertEquals(4, status / 100, "unexpected status: " + status);
        }
    }

    @Test
    void setLastRuntime_invalidResourceType_returnsClientError() {
        try (Response r = put("notAResourceType",
                              body(entry(Instant.now().toEpochMilli(), TEST_NODE_ID)))) {
            int status = r.getStatus();
            assertEquals(4, status / 100, "unexpected status: " + status);
        }
    }

    @Test
    void getLastRuntime_afterSet_returnsAllEntriesWithEpochMillis() {
        long now = Instant.now().toEpochMilli();
        long ts1 = now - 120_000;
        long ts2 = now - 90_000;
        try (Response r = put(TYPE, body(entry(ts1, TEST_NODE_ID), entry(ts2, TEST_NODE_ID_2)))) {
            assertEquals(204, r.getStatus(), "unexpected PUT status");
        }

        try (Response r = get(TYPE)) {
            String body = r.readEntity(String.class);
            log.debug("GET status={}, body={}", r.getStatus(), body);
            assertEquals(200, r.getStatus(), "unexpected GET status");

            JsonNode root = MAPPER.readTree(body);
            assertTimestamp(ts1, findEntry(root, TEST_NODE_ID), body);
            assertTimestamp(ts2, findEntry(root, TEST_NODE_ID_2), body);
        }
    }

    @Test
    void getLastRuntime_invalidResourceType_returnsClientError() {
        try (Response r = get("notAResourceType")) {
            int status = r.getStatus();
            assertEquals(4, status / 100, "unexpected status: " + status);
        }
    }

    @Test
    void getLastRuntime_noEntries_returnsNotFound() {
        try (Response r = get(EMPTY_TYPE.name())) {
            assertEquals(404, r.getStatus(), "unexpected status");
        }
    }

    private static JsonNode findEntry(JsonNode root, String nodeId) {
        JsonNode entries = root.get("runtimes");
        assertNotNull(entries, "runtimes missing from response");
        for (JsonNode entry : entries) {
            if (nodeId.equals(entry.get("nodeId").asString())) {
                return entry;
            }
        }
        return null;
    }

    private Response get(String resourceType) {
        return client.target(baseUrl)
            .path(URI_PREFIX)
            .path(PRIVATE_PATH)
            .path(LAST_RUNTIME_PATH)
            .path(resourceType)
            .request()
            .get();
    }

    // Timestamps are Instants, exchanged as integer milliseconds since the epoch
    private static void assertTimestamp(long expectedEpochMillis, JsonNode entry, String body) {
        assertNotNull(entry, "missing entry in: " + body);
        JsonNode ts = entry.get("timestamp");
        assertNotNull(ts, "timestamp missing in: " + body);
        assertTrue(ts.isIntegralNumber(), "timestamp must be epoch millis, but was: " + ts);
        assertEquals(expectedEpochMillis, ts.asLong(), "timestamp mismatch");
    }

    private static ObjectNode entry(long epochMillis, String nodeId) {
        return MAPPER.createObjectNode().put("timestamp", epochMillis).put("nodeId", nodeId);
    }

    private static String body(ObjectNode... entries) {
        ArrayNode array = MAPPER.createArrayNode();
        for (ObjectNode e : entries) {
            array.add(e);
        }
        return MAPPER.createObjectNode().set("runtimes", array).toString();
    }

    private Response put(String resourceType, String jsonBody) {
        return client.target(baseUrl)
            .path(URI_PREFIX)
            .path(PRIVATE_PATH)
            .path(LAST_RUNTIME_PATH)
            .path(resourceType)
            .request()
            .put(Entity.json(jsonBody));
    }
}
