package org.dataone.notifications.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.dataone.notifications.NsConfig;
import org.dataone.notifications.event.LastSuccessfulRuntimes;
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

     /// Sends an HTTP GET to the internal api service and deserializes the JSON response to the
     /// provided `Class<T>` type.
     ///
     /// Example: fetch the last successful runtimes (see {@code PrivateResources.getLastRuntime})
     /// and iterate over the timestamp / nodeId pairs. The typed overload doGet(String, Class) does
     /// the JSON-to-object conversion:
     ///
     /// ```java
     ///   LastSuccessfulRuntimes result = HttpUtils.doGet(
     ///       "/lastSuccessfulRuntime/datasetChanges", LastSuccessfulRuntimes.class);
     ///   for (LastSuccessfulRuntimes.Entry e : result.runtimes()) {
     ///       long epochMillis = e.timestamp();   // milliseconds since the epoch (UTC)
     ///       String nodeId = e.nodeId();
     ///       Instant lastRun = Instant.ofEpochMilli(epochMillis);  // if an Instant is needed
     ///   }
     /// ```
     ///
     /// @param requestUri see {@link #doGet(String)}
     /// @param type       the class (e.g. a record or POJO) to deserialize the response body into
     /// @return the deserialized response as a class of the requested type
     /// @throws IOException if the request fails (see {@link #doGet(String)}) or the response body
     ///                     cannot be mapped to the given type
     ///
    public static <T> T doGet(String requestUri, Class<T> type) throws IOException {
        String body = doGet(requestUri);
        try {
            return MAPPER.readValue(body, type);
        } catch (JacksonException e) {
            throw new IOException(
                "Unable to parse response of GET " + requestUri + " as " + type.getName(), e);
        }
    }

     /// Sends an HTTP GET to the internal api service and returns the JSON response as a String.
     ///
     /// @param requestUri path (plus optional query) E.g. "/lastSuccessfulRuntime/datasetChanges".
     ///                   Will be appended to the base URL (see ns.api.privateBaseUrl in
     ///                   config/properties.yaml). Callers are responsible for URL-encoding any
     ///                   path segments.
     ///
     /// @return the response body (JSON) as a String
     /// @throws IOException if the base URL env var is not set, the request fails, or the server
     ///                     returns a non-2xx status
     ///
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

     /// Sends an HTTP PUT to the internal api service, to set the last successful runtimes. The
     /// entries are wrapped in a {@link LastSuccessfulRuntimes} and sent as JSON, with timestamps
     /// as integer milliseconds since the epoch. E.g.:
     ///
     /// ```java
     ///   HttpUtils.doPut("/lastSuccessfulRuntime/datasetChanges", List.of(
     ///       new LastSuccessfulRuntimes.Entry(1794338367000L, "urn:node:KNB"),
     ///       new LastSuccessfulRuntimes.Entry(1794338379000L, "urn:node:ARCTIC")));
     /// }
     /// ```
     /// @param requestUri path (plus optional query) E.g. "/lastSuccessfulRuntime/datasetChanges".
     ///                   Will be appended to the base URL (see ns.api.privateBaseUrl in
     ///                   config/properties.yaml).  Callers are responsible for URL-encoding any path
     ///                   segments.
     /// @param entries    the timestamp and nodeId pairs to set
     /// @return the response body as a String (empty if the server returns 204 No Content)
     /// @throws IOException if the body cannot be serialized, the request fails, or the server
     ///                     returns a non-2xx status
     ///
    public static String doPut(String requestUri, List<LastSuccessfulRuntimes.Entry> entries)
        throws IOException {

        String path = requestUri.startsWith("/") ? requestUri : "/" + requestUri;

        String json;
        try {
            json = MAPPER.writeValueAsString(new LastSuccessfulRuntimes(entries));
        } catch (JacksonException e) {
            throw new IOException("Unable to serialize body of PUT " + path + " as JSON", e);
        }

        HttpRequest request =
            HttpRequest.newBuilder(URI.create(BASE_URL + path)).timeout(TIMEOUT_SEC)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)).build();
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
