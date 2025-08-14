package org.dataone.notifications.smoketests;


import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * A smoke test for the notifications API.
 * NOTE: Smoke tests require a running instance of the application!
 * They comprise a small collection of tests, used to verify that the installed application is
 * working as expected, after a deployment or upgrade.
 */
class NotificationsApiSmokeIT {

    private static String baseUrl;
    private static String token;

    @BeforeAll
    static void setup() {
        baseUrl = System.getProperty("BASE_URL", System.getenv().getOrDefault("BASE_URL", "http://localhost:8080"));
        token = System.getProperty("TOKEN", System.getenv("TOKEN"));
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("TOKEN is required");
        }
        System.out.println("BASE_URL: " + baseUrl);
        System.out.println("TOKEN: " + token.substring(0, 5) + "...");
        RestAssured.baseURI = baseUrl;
    }

    private String pid(int n) {
        return String.format("urn:uuid:this-is-test-pid-%02d", n);
    }

    @Test
    void createListDeleteRoundtrip() {
        // Create 10
        for (int i = 1; i <= 10; i++) {
            String id = pid(i);
            System.out.println("* * * Creating: " + id);
            given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("{}")
            .when()
                .post("/notifications/datasets/{pid}", id)
            .then()
                .log().ifValidationFails()
                .statusCode(anyOf(is(200), is(201)))
                .contentType(any(String.class)); // adapt as needed
        }

        // Verify all present
        List<String> list =
            given()
                .header("Authorization", "Bearer " + token)
            .when()
                .get("/notifications/datasets")
            .then()
                .log().ifValidationFails()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .jsonPath()
                .getList("resourceIds", String.class);

        for (int i = 1; i <= 10; i++) {
            String id = pid(i);
            System.out.println("* * * Verifying: " + id);
            org.junit.jupiter.api.Assertions.assertTrue(
                list.contains(id),
                "Missing: " + id
            );
        }

        // Delete
        for (int i = 1; i <= 10; i++) {
            String id = pid(i);
            System.out.println("* * * Deleting: " + id);
            given()
                .header("Authorization", "Bearer " + token)
            .when()
                .delete("/notifications/datasets/{pid}", id)
            .then()
                .log().ifValidationFails()
                .statusCode(anyOf(is(200), is(204)));
        }

        // Verify empty
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/notifications/datasets")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("resourceIds", hasSize(0));
    }
}
