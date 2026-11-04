package org.dataone.notifications.eventsource.data;

import org.apache.solr.client.solrj.util.ClientUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.dataone.notifications.eventsource.solr.SolrQueryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The class to poll the solr server and find the candidate objects for the notification service
 */
public class DataSource {
    private static final Logger log = LoggerFactory.getLogger(DataSource.class);
    private static final String solrQueryUrl = "";
    private static final SolrQueryClient solrClient = new SolrQueryClient(solrQueryUrl);


    /**
     * Poll the solr server and send the candidate objects to the notification queue
     * @throws IOException
     * @throws InterruptedException
     */
    public void poll()
        throws IOException, InterruptedException {
        Map<String, String> notificationMnLatestMap = null;
        if (notificationMnLatestMap == null || notificationMnLatestMap.isEmpty()) {
            return;
        }
        // Build the MN/date conditions.
        List<String> mnQueries = new ArrayList<>();
        for (Map.Entry<String, String> entry : notificationMnLatestMap.entrySet()) {
            String mn = entry.getKey();
            String dateModified = entry.getValue();
            mnQueries.add(
                "(authoritativeMN:\"" + escapeSolrValue(mn) + "\" " +
                    "AND dateModified:{" + dateModified + " TO *})"
            );
        }
        // Combine all MN/date conditions with OR.
        String mnDateQuery = "(" + String.join(" OR ", mnQueries) + ")";
        // Add the common conditions.
        String q = mnDateQuery +
            " AND obsoletedBy:[* TO *]" +
            " AND (formatType:\"RESOURCE\" OR formatType:\"METADATA\")";
        String cursorMark = "*";
        while (true) {
            Map<String, String> params = new HashMap<>();
            params.put("q", q);
            params.put("fl", "id,authoritativeMN,obsoletedBy");
            params.put("sort", "id asc");
            params.put("cursorMark", cursorMark);
            params.put("rows", "1000");
            params.put("wt", "json");
            // Reuse your SolrQueryClient query method here.
            JsonNode result = solrClient.query(params);
            JsonNode documents = result.path("response").path("docs");
            // Process this batch.
            for (JsonNode doc : documents) {
                String guid = doc.path("id").asText(null);
                String newId = doc.path("obsoletedBy").asText(null);
                String nodeId = doc.path("authoritativeMN").asText(null);
                log.debug(
                    "The id is {}, the new id is {} and the node id is {}", guid, newId, nodeId);
                // TODO: Submit the notification task.
            }
            // Solr returns the cursor to use for the next request.
            String nextCursorMark = result.path("nextCursorMark").asText();
            // No more results.
            if (nextCursorMark.equals(cursorMark)) {
                break;
            }
            cursorMark = nextCursorMark;
        }
    }

    private String escapeSolrValue(String value) {
        return ClientUtils.escapeQueryChars(value);
    }

    private Map<String, String> getMNLatestMap() {
        return null;
    }

}
