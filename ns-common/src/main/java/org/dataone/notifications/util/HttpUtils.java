package org.dataone.notifications.util;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import org.dataone.notifications.NsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * A utility class for HTTP requests. All requests are made via the internal K8s service, so they
 * do not leave and re-enter the cluster. This reduces network overhead and also allows access to
 * the private endpoints in org.dataone.notifications.api.internal
 */
public class HttpUtils {

    private static final Logger log = LoggerFactory.getLogger(HttpUtils.class);
    private static final Duration TIMEOUT_SEC = Duration.ofSeconds(10);
    private static final HttpClient CLIENT =
        HttpClient.newBuilder().connectTimeout(TIMEOUT_SEC).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_URL = loadBaseUrl();

    private static String loadBaseUrl() {
        String value = NsConfig.getConfig().getString("ns.api.privateBaseUrl");
        log.info("Loaded base URL from config: {}", value);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "ns.api.privateBaseUrl not set: " + value);
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * Sends an HTTP GET to the internal api service and deserializes the JSON response.
     *
     * @param requestUri see {@link #doGet(String)}
     * @param type       the class (e.g. a record or POJO) to deserialize the response body into
     * @return the deserialized response
     * @throws IOException if the request fails (see {@link #doGet(String)}) or the response body
     *                     cannot be mapped to the given type
     */
    public static <T> T doGet(String requestUri, Class<T> type) throws IOException {
        String body = doGet(requestUri);
        try {
            return MAPPER.readValue(body, type);
        } catch (JacksonException e) {
            throw new IOException(
                "Unable to parse response of GET " + requestUri + " as " + type.getName(), e);
        }
    }

    /**
     * Sends an HTTP GET to the internal api service.
     *
     * @param requestUri path (plus optional query) E.g. "/lastSuccessfulRuntime/datasetChanges".
     *                   Will be appended to the base URL (see ns.api.privateBaseUrl in
     *                   config/properties.yaml).  Callers are responsible for URL-encoding any path
     *                   segments.
     * @return the response body (JSON) as a String
     * @throws IOException if the base URL env var is not set, the request fails, or the server
     *                     returns a non-2xx status
     */
    public static String doGet(String requestUri) throws IOException {

        String path = requestUri.startsWith("/") ? requestUri : "/" + requestUri;

        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
            .timeout(TIMEOUT_SEC)
            .header("Accept", "application/json")
            .GET()
            .build();
        try {
            HttpResponse<String> response =
                CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IOException(
                    "GET " + path + " failed with HTTP " + status + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during GET " + path, e);
        }
    }

    /**
     * Sends an HTTP PUT with an application/x-www-form-urlencoded body to the internal api
     * service.
     *
     * @param requestUri path (plus optional query) E.g. "/lastSuccessfulRuntime/datasetChanges".
     *                   Will be appended to the base URL (see ns.api.privateBaseUrl in
     *                   config/properties.yaml).  Callers are responsible for URL-encoding any path
     *                   segments.
     * @param formFields form field names and values; values are converted with toString() and
     *                   URL-encoded here. Null values are skipped.
     * @return the response body (JSON) as a String
     * @throws IOException if the request fails or the server returns a non-2xx status
     */
    public static String doPut(String requestUri, Map<String, ?> formFields) throws IOException {

        String path = requestUri.startsWith("/") ? requestUri : "/" + requestUri;

        String form = formFields.entrySet().stream().filter(e -> e.getValue() != null).map(
            e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(
                e.getValue().toString(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));

        HttpRequest request =
            HttpRequest.newBuilder(URI.create(BASE_URL + path)).timeout(TIMEOUT_SEC)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .PUT(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            HttpResponse<String> response =
                CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IOException(
                    "PUT " + path + " failed with HTTP " + status + ": " + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during PUT " + path, e);
        }
    }

}
