package org.dataone.notifications.smoketests;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void setLastRuntime_validRequest_returnsResult() throws Exception {
        long timestamp = Instant.now().minusSeconds(60).toEpochMilli();
        log.debug("PUT lastSuccessfulRuntime, timestamp={}", timestamp);

        try (Response r = put(form(String.valueOf(timestamp),
                                   SubscriptionResourceType.testType.name(), TEST_NODE_ID))) {
            assertEquals(200, r.getStatus(), "unexpected status");
            String body = r.readEntity(String.class);
            log.debug("Response body: {}", body);

            JsonNode entries = MAPPER.readTree(body).get("entries");
            assertNotNull(entries, "entries missing from response");
            assertEquals(1, entries.size(), "expected exactly one entry");
            assertEquals(TEST_NODE_ID, entries.get(0).get("nodeId").asString());
            assertEquals(timestamp, toInstant(entries.get(0).get("timestamp")).toEpochMilli(),
                         "timestamp mismatch");
        }
    }

    @Test
    void setLastRuntime_timestampInFuture_returnsBadRequest() {
        long timestamp = Instant.now().plusSeconds(3600).toEpochMilli();
        try (Response r = put(form(String.valueOf(timestamp),
                                   SubscriptionResourceType.testType.name(), TEST_NODE_ID))) {
            assertEquals(400, r.getStatus(), "future timestamp should be rejected");
        }
    }

    @Test
    void setLastRuntime_timestampTooOld_returnsBadRequest() {
        long timestamp = Instant.now().minusSeconds(8L * 24 * 60 * 60).toEpochMilli();
        try (Response r = put(form(String.valueOf(timestamp),
                                   SubscriptionResourceType.testType.name(), TEST_NODE_ID))) {
            assertEquals(400, r.getStatus(), "timestamp older than 7 days should be rejected");
        }
    }

    @Test
    void setLastRuntime_missingParam_returnsBadRequest() {
        Form noNodeId = new Form()
            .param("timestamp", String.valueOf(Instant.now().toEpochMilli()))
            .param("resourceType", SubscriptionResourceType.testType.name());
        try (Response r = put(noNodeId)) {
            assertEquals(400, r.getStatus(), "missing nodeId should be rejected");
        }
    }

    @Test
    void setLastRuntime_invalidResourceType_returnsClientError() {
        try (Response r = put(form(String.valueOf(Instant.now().toEpochMilli()),
                                   "notAResourceType", TEST_NODE_ID))) {
            int status = r.getStatus();
            assertEquals(4, status / 100, "unexpected status: " + status);
        }
    }

    @Test
    void getLastRuntime_afterSet_returnsSetTimestamp() throws Exception {
        long timestamp = Instant.now().minusSeconds(120).toEpochMilli();
        String type = SubscriptionResourceType.testType.name();
        try (Response r = put(form(String.valueOf(timestamp), type, TEST_NODE_ID))) {
            assertEquals(200, r.getStatus(), "unexpected PUT status");
        }

        try (Response r = get(type)) {
            String body = r.readEntity(String.class);
            log.debug("GET status={}, body={}", r.getStatus(), body);
            assertEquals(200, r.getStatus(), "unexpected GET status");

            JsonNode entry = findEntry(MAPPER.readTree(body), TEST_NODE_ID);
            assertNotNull(entry, "no entry for " + TEST_NODE_ID + " in: " + body);
            assertEquals(timestamp, toInstant(entry.get("timestamp")).toEpochMilli(),
                         "timestamp mismatch");
        }
    }

    @Test
    void getLastRuntime_returnsAllTestNodes() throws Exception {
        String type = SubscriptionResourceType.testType.name();
        long timestamp = Instant.now().minusSeconds(90).toEpochMilli();
        for (String nodeId : new String[] {TEST_NODE_ID, TEST_NODE_ID_2}) {
            try (Response r = put(form(String.valueOf(timestamp), type, nodeId))) {
                assertEquals(200, r.getStatus(), "unexpected PUT status");
            }
        }

        try (Response r = get(type)) {
            String body = r.readEntity(String.class);
            assertEquals(200, r.getStatus(), "unexpected GET status");

            JsonNode root = MAPPER.readTree(body);
            assertNotNull(findEntry(root, TEST_NODE_ID), "missing " + TEST_NODE_ID + ": " + body);
            assertNotNull(findEntry(root, TEST_NODE_ID_2),
                          "missing " + TEST_NODE_ID_2 + ": " + body);
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
        JsonNode entries = root.get("entries");
        assertNotNull(entries, "entries missing from response");
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

    // Jackson serializes Instant as either epoch seconds (decimal) or an ISO-8601 string
    private static Instant toInstant(JsonNode ts) {
        if (ts.isNumber()) {
            java.math.BigDecimal secs = ts.decimalValue();
            return Instant.ofEpochSecond(secs.longValue(),
                secs.remainder(java.math.BigDecimal.ONE).movePointRight(9).longValue());
        }
        return Instant.parse(ts.asString());
    }

    private Response put(Form form) {
        return client.target(baseUrl)
            .path(URI_PREFIX)
            .path(PRIVATE_PATH)
            .path(LAST_RUNTIME_PATH)
            .request()
            .put(Entity.form(form));
    }

    private static Form form(String timestamp, String resourceType, String nodeId) {
        return new Form()
            .param("timestamp", timestamp)
            .param("resourceType", resourceType)
            .param("nodeId", nodeId);
    }
}
