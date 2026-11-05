package org.dataone.notifications.smoketests;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.Response;
import org.dataone.notifications.api.resource.SubscriptionResourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke tests for the private (cluster-only) endpoints. Requires BASE_URL and a valid TOKEN. Usage:
 *  *
 *  * $ export TOKEN=valid-JWT-token
 *  * $ export BASE_URL=http://localhost:8080/notifications/v1
 *  * $ mvn clean -PsmokeTest -DBASE_URL="$BASE_URL" -DTOKEN="$TOKEN" verify
 */
class PrivateResourcesSmokeIT {

    private static final String URI_PREFIX = "notifications/v1";
    private static final String PRIVATE_PATH = "private";
    private static final String LAST_RUNTIME_PATH = "lastSuccessfulRuntime";
    private static final String TEST_NODE_ID = "urn:node:SMOKE_TEST";
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
                                   SubscriptionResourceType.datasetChanges.name(), TEST_NODE_ID))) {
            assertEquals(200, r.getStatus(), "unexpected status");
            String body = r.readEntity(String.class);
            log.debug("Response body: {}", body);

            JsonNode root = MAPPER.readTree(body);
            assertEquals(SubscriptionResourceType.datasetChanges.name(),
                         root.get("resourceType").asString());
            assertEquals(TEST_NODE_ID, root.get("nodeId").asString());
        }
    }

    @Test
    void setLastRuntime_timestampInFuture_returnsBadRequest() {
        long timestamp = Instant.now().plusSeconds(3600).toEpochMilli();
        try (Response r = put(form(String.valueOf(timestamp),
                                   SubscriptionResourceType.datasetChanges.name(), TEST_NODE_ID))) {
            assertEquals(400, r.getStatus(), "future timestamp should be rejected");
        }
    }

    @Test
    void setLastRuntime_timestampTooOld_returnsBadRequest() {
        long timestamp = Instant.now().minusSeconds(8L * 24 * 60 * 60).toEpochMilli();
        try (Response r = put(form(String.valueOf(timestamp),
                                   SubscriptionResourceType.datasetChanges.name(), TEST_NODE_ID))) {
            assertEquals(400, r.getStatus(), "timestamp older than 7 days should be rejected");
        }
    }

    @Test
    void setLastRuntime_missingParam_returnsBadRequest() {
        Form noNodeId = new Form()
            .param("timestamp", String.valueOf(Instant.now().toEpochMilli()))
            .param("resourceType", SubscriptionResourceType.datasetChanges.name());
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
