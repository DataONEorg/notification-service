package org.dataone.notifications.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * A utility class for HTTP requests. All requests are made via the internal K8s service, so they
 * do not leave and re-enter the cluster. This reduces network overhead and also allows access to
 * the private endpoints in org.dataone.notifications.api.internal
 */
public class HttpUtils {

    /** Env var holding the base URL of the internal K8s service, e.g. http://ns-api:8080 */
    public static final String BASE_URL_ENV_VAR = "NS_API_INTERNAL_BASE_URL";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient CLIENT =
        HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_URL = loadBaseUrl();

    private static String loadBaseUrl() {
        String value = System.getenv(BASE_URL_ENV_VAR);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Environment variable not set: " + BASE_URL_ENV_VAR);
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
     * @param requestUri path (plus optional query) appended to the base URL, e.g.
     *                   "/lastSuccessfulRuntime/datasetChanges". Callers are responsible for
     *                   URL-encoding any path segments.
     * @return the response body (JSON) as a String
     * @throws IOException if the base URL env var is not set, the request fails, or the server
     *                     returns a non-2xx status
     */
    public static String doGet(String requestUri) throws IOException {

        String path = requestUri.startsWith("/") ? requestUri : "/" + requestUri;

        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
            .timeout(TIMEOUT)
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

}
