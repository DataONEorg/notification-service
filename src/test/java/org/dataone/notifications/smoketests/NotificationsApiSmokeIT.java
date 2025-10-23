package org.dataone.notifications.smoketests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.dataone.notifications.api.resource.ResourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationsApiSmokeIT {

    private static final String URI_PREFIX = "notifications/v1/subscriptions";
    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String baseUrl;
    private String token;
    private Client client;

    @BeforeEach
    void setUp() {
        baseUrl = System.getProperty("BASE_URL", System.getenv("BASE_URL"));
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("BASE_URL is required for these smoke tests");
        }

        token = System.getProperty("TOKEN", System.getenv("TOKEN"));
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("TOKEN is required for these smoke tests");
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
    void createListDeleteRoundtrip() {
        final int totPids = 10;
        List<String> testPids = getTestPids(totPids);

        log.debug("BASE_URL: {}", baseUrl);
        log.debug("TOKEN: {}...", token.substring(0, 5));
        String url = client.target(baseUrl)
            .path(URI_PREFIX)
            .path(ResourceType.datasetChanges.name())
            .getUri()
            .toString();
        log.debug("Request URL: {}", url);

        List<String> initList = getSubscriptions();
        int initCount = initList.size();
        log.debug("Beginning State ({} pre-existing subscriptions\n{})", initCount,
                  prettyPrint(initList));

        for (String pid : testPids) {
            log.debug("Adding subscription for pid: " + pid);
            Response r = client.target(baseUrl)
                .path(URI_PREFIX)
                .path(ResourceType.datasetChanges.name())
                .path(pid)
                .request()
                .header("Authorization", "Bearer " + token)
                .post(Entity.json("{}"));
            try {
                int status = r.getStatus();
                assertTrue(status == 200 || status == 201, "unexpected post status: " + status);
            } finally {
                r.close();
            }
        }

        List<String> subsList = getSubscriptions();
        log.debug("State after {} subscriptions: \n{}", totPids, prettyPrint(subsList));
        assertEquals(
            initCount + totPids, subsList.size(), "unexpected subscription count after adds");
        for (String pid : testPids) {
            log.debug("Verifying: " + pid);
            assertTrue(subsList.contains(pid), "Missing: " + pid);
        }

        for (String pid : testPids) {
            log.debug("Deleting subscription for pid: " + pid);
            Response r = client.target(baseUrl)
                .path(URI_PREFIX)
                .path(ResourceType.datasetChanges.name())
                .path(pid)
                .request()
                .header("Authorization", "Bearer " + token)
                .delete();
            try {
                int status = r.getStatus();
                assertTrue(status == 200 || status == 204, "unexpected delete status: " + status);
            } finally {
                r.close();
            }
        }

        List<String> endList = getSubscriptions();
        log.debug("State after removing added subscriptions:\n{}", prettyPrint(endList));
        assertEquals(initCount, endList.size(), "end count should match initial count");
    }

    private List<String> getSubscriptions() {
        Response r = client.target(baseUrl)
            .path(URI_PREFIX)
            .path(ResourceType.datasetChanges.name())
            .request()
            .header("Authorization", "Bearer " + token)
            .get();

        try {
            assertEquals(200, r.getStatus(), "GET subscriptions failed: " + r.getStatus());
            String body = r.readEntity(String.class);

            if (body == null || body.isBlank()) {
                return new ArrayList<>();
            }

            try {
                JsonNode root = MAPPER.readTree(body);
                JsonNode arr = root.get("resourceIds");
                List<String> result = new ArrayList<>();
                if (arr != null && arr.isArray()) {
                    for (JsonNode n : arr) {
                        result.add(n.asText());
                    }
                }
                return result;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse response JSON", e);
            }
        } finally {
            r.close();
        }
    }

    private static List<String> getTestPids(int count) {
        List<String> testPids = new ArrayList<>();
        long base = System.nanoTime();
        for (int i = 1; i <= count; i++) {
            testPids.add(String.format("urn:uuid:test-pid-%d-%02d", base, i));
        }
        return testPids;
    }

    private static String prettyPrint(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s).append("\n");
        }
        return sb.toString();
    }
}
