package org.dataone.notifications.smoketests;


import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.dataone.notifications.api.resource.ResourceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A smoke test for the notifications API.
 * NOTE: Smoke tests require a running instance of the application!
 * They comprise a small collection of tests, used to verify that the installed application is
 * working as expected, after a deployment or upgrade.
 * Run smoke tests with:
 * $ mvn verify -PsmokeTest -DBASE_URL="$BASE_URL" -DTOKEN="$TOKEN"
 *
 * http logging output from Rest Assured is set to log level WARN by default. For more-verbose
 * output, override from the command line, using '-DLOG_LEVEL='; e.g.:
 *
 * $ mvn verify mvn -PsmokeTest -DBASE_URL="$BASE_URL" -DTOKEN="$TOKEN" -DLOG_LEVEL=debug
 */
class NotificationsApiSmokeIT {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private static String baseUrl;
    private static String token;

    @BeforeAll
    static void setup() {
        baseUrl = System.getProperty("BASE_URL", System.getenv("BASE_URL"));
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                "BASE_URL is required - e.g. mvn verify -DBASE_URL=http://localhost:8080");
        }
        token = System.getProperty("TOKEN", System.getenv("TOKEN"));
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                "TOKEN is required - e.g. export TOKEN=eyJhb..etc; mvn verify -DTOKEN=\"$TOKEN\"");
        }
        RestAssured.baseURI = baseUrl;
    }

    @Test
    void createListDeleteRoundtrip() {
        log.debug("BASE_URL: {}", baseUrl);
        log.debug("TOKEN: {}...", token.substring(0, 5));
        int totPids = 10;
        final List<String> testPids = getTestPids(totPids);
        List<String> initList = getSubscriptions();
        int initCount = initList.size();
        log.debug(
            "Beginning State (found {} pre-existing subscriptions):\n{}", initCount,
            prettyPrint(initList));

        // Create totPids
        for (String pid : testPids) {
            log.debug("Adding subscription for pid: " + pid);
            given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{}")
            .when()
                .post("/notifications/{resource}/{pid}", ResourceType.datasetChanges, pid)
            .then()
                .log().ifValidationFails()
                .statusCode(anyOf(is(200), is(201)))
                .contentType(any(String.class)); // adapt as needed
        }

        // Verify all present
        List<String> subsList = getSubscriptions();
        int subsCount = subsList.size();
        int expectedSubsCount = initCount + totPids;
        log.debug("State after {} subscriptions: \n{}", totPids, prettyPrint(subsList));
        assertEquals(expectedSubsCount, subsCount,
                     "subscriptions count total should be beginning count ("
                         + initCount + ") plus " + totPids + " added");

        for (String pid : testPids) {
            log.debug("Verifying: " + pid);
            assertTrue(subsList.contains(pid), "Missing: " + pid);
        }

        // Delete
        for (String pid : testPids) {
            log.debug("Deleting subscription for pid: " + pid);
            given()
                .header("Authorization", "Bearer " + token)
            .when()
                .delete("/notifications/{resource}/{pid}", ResourceType.datasetChanges, pid)
            .then()
                .log().ifValidationFails()
                .statusCode(anyOf(is(200), is(204)));
        }

        // Verify all deleted
        List<String> endList = getSubscriptions();
        int endCount = endList.size();
        log.debug("State after removing added subscriptions:\n{}", prettyPrint(endList));
        assertEquals(initCount, endCount,
                     "end count should be same as beginning count (" + initCount + ")");
    }

    private static List<String> getSubscriptions() {
        return
            given()
                .header("Authorization", "Bearer " + token)
            .when()
                .get("/notifications/{resource}", ResourceType.datasetChanges)
            .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath()
                .getList("resourceIds", String.class);
    }

    private static String prettyPrint(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s).append("\n");
        }
        return sb.toString();
    }

    private static List<String> getTestPids(int count) {
        List<String> testPids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            long t = System.currentTimeMillis();
            testPids.add(String.format("urn:uuid:test-pid-%d-%02d", t, i));
        }
        return testPids;
    }
}
