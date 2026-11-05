
package org.dataone.notifications.eventsource.data.solr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * A client class to query a Solr server
 */
public class SolrQueryClient {
    private static final Logger log = LoggerFactory.getLogger(SolrQueryClient.class);
    private static final Set<String> ALLOWED_PARAMS =
        Set.of("q", "fl", "sort", "rows", "cursorMark", "start", "fq", "wt", "facet",
               "facet.field", "facet.limit", "facet.mincount", "json.facet");
    private static final String JSON = "json";
    private static final String WT = "wt";
    private final String solrQueryUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructor of the class
     * @param solrQueryUrl  the query url of the solr server
     */
    public SolrQueryClient(String solrQueryUrl) {
        this.solrQueryUrl = solrQueryUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Query the solr server with the given params.
     * @param params  the key/value pairs for the query. The key can't be encoded and the allowed
     *               key values are in the ALLOWED_PARAMS map
     * @return the json response from the solr server
     * @throws IOException
     * @throws InterruptedException
     */
    public JsonNode query(Map<String, String> params) throws IOException, InterruptedException {
        boolean setWT = false;
        StringBuilder formData = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey().isBlank()) {
                continue;
            }
            validateParamEnty(entry);
            if (formData.length() > 0) {
                formData.append("&");
            }
            if(WT.equals(entry.getKey())) {
                if (!JSON.equals(entry.getValue())) {
                    throw new IllegalArgumentException(
                        "The value of the parameter wt can only be " + JSON + " rather than "
                            + entry.getValue());
                } else {
                    // The parameter already has wt=json
                    setWT = true;
                }
            }
            formData.append(URLEncoder.encode(
                entry.getKey(), StandardCharsets.UTF_8));
            formData.append("=");
            formData.append(URLEncoder.encode(
                entry.getValue(), StandardCharsets.UTF_8));
        }
        //  Add the parameter of wt with value json
        if (!setWT) {
            log.debug("Append wt=json");
            if (formData.length() > 0) {
                formData.append("&");
            }
            formData.append(WT);
            formData.append("=");
            formData.append(JSON);
        }
        String paramString = formData.toString();
        log.debug("The solr parameter string is {}", paramString);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(solrQueryUrl))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type",
                    "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(paramString))
            .build();

        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException(
                "Solr request failed. HTTP status: "
                    + response.statusCode()
                    + ", response: " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    /**
     * Make sure there is no encoding in the key and key is in the allowed set.
     * @param entry  the entry will be examined
     */
    protected static void validateParamEnty(Map.Entry<String, String> entry) {
        if (entry != null) {
            String key = entry.getKey();
            if (key.isBlank() || key.contains("%") || !ALLOWED_PARAMS.contains(key)) {
                throw new IllegalArgumentException("Invalid Solr parameter: " + key);
            }
        }
    }
}
