package org.dataone.notifications.eventsource.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.solr.client.solrj.util.ClientUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.dataone.notifications.eventsource.solr.SolrQueryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The class to poll the solr server and find the candidate objects for the notification service
 */
public class DataSet {
    private static final Logger log = LoggerFactory.getLogger(DataSet.class);
    private static final String solrQueryUrl =
        "http://localhost:8983/solr/metacat-index/select-all";
    private static final SolrQueryClient solrQueryClient = new SolrQueryClient(solrQueryUrl);

    private static final String MN_LATEST_MODIFICATION_FILE_PATH =
        "./.mn_latest_modification_time_map";


    /**
     * Poll the solr server and send the candidate objects to the notification queue
     * @throws IOException
     * @throws InterruptedException
     */
    public void poll() throws IOException, InterruptedException {
        List<String> nodeIds = getDistinctAuthoritativeMNs();
        if (nodeIds.isEmpty()) {
            log.warn(
                "The notification service cannot find any nodeIds in the solr service {}. So it "
                    + "will stop polling.", solrQueryUrl);
            return;
        }
        Map<String, String> originalMnLatestModifiedDateMap = getMnLatestMap(nodeIds);
        if (originalMnLatestModifiedDateMap == null || originalMnLatestModifiedDateMap.isEmpty()) {
            return;
        }
        // Build the MN/date conditions.
        List<String> mnQueries = new ArrayList<>();
        for (Map.Entry<String, String> entry : originalMnLatestModifiedDateMap.entrySet()) {
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
        // A new map to store the latest modification date for this poll
        Map<String, String> latestModifiedMap = new HashMap<>();
        while (true) {
            Map<String, String> params = new HashMap<>();
            params.put("q", q);
            params.put("fl", "id,authoritativeMN,obsoletedBy,dateModified");
            params.put("sort", "dateModified desc,id asc");
            params.put("cursorMark", cursorMark);
            params.put("rows", "1000");
            params.put("wt", "json");
            // Reuse your SolrQueryClient query method here.
            JsonNode result = solrQueryClient.query(params);
            JsonNode documents = result.path("response").path("docs");
            // Process this batch.
            for (JsonNode doc : documents) {
                String guid = doc.path("id").asText(null);
                String newId = doc.path("obsoletedBy").asText(null);
                String nodeId = doc.path("authoritativeMN").asText(null);
                String modifiedDate = doc.path("dateModified").asText(null);
                // Since the modficationDate is desc, the first record has the newest value
                if (nodeId != null && modifiedDate != null) {
                    latestModifiedMap.putIfAbsent(nodeId, modifiedDate);
                }
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
        // Update the original map with new values
        originalMnLatestModifiedDateMap.putAll(latestModifiedMap);
        saveMnLatestMap(originalMnLatestModifiedDateMap);
    }

    private String escapeSolrValue(String value) {
        return ClientUtils.escapeQueryChars(value);
    }

    private Map<String, String> getMnLatestMap(List<String> nodeIds) throws IOException {
        File file = new File(MN_LATEST_MODIFICATION_FILE_PATH);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> mnLatestMap = new HashMap<>();
        // Read the existing map if the file exists.
        if (file.exists()) {
            mnLatestMap = mapper.readValue(file,
                                           new TypeReference<Map<String, String>>() {});
        }
        // Add new node IDs with the current timestamp.
        String currentTime = Instant.now().toString();
        boolean updated = false;
        for (String nodeId : nodeIds) {
            if (!mnLatestMap.containsKey(nodeId)) {
                mnLatestMap.put(nodeId, currentTime);
                updated = true;
            }
        }
        // Save the map if the file is new or has been updated.
        if (!file.exists() || updated) {
            saveMnLatestMap(mnLatestMap);
        }
        return mnLatestMap;
    }

    private void saveMnLatestMap(Map<String, String> map) throws IOException {
        File file = new File(MN_LATEST_MODIFICATION_FILE_PATH);
        // Create parent directories if they don't exist.
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            Files.createDirectories(parentDir.toPath());
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, map);
    }

    /**
     * Get the list of the distinct authoritativeMN id from the solr server
     * @return a list of the distinct authoritativeMN. An empty list will be returned if there is
     * no result.
     * @throws IOException
     * @throws InterruptedException
     */
    protected List<String> getDistinctAuthoritativeMNs() throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("q", "*:*");
        params.put("rows", "0");
        params.put("facet", "true");
        params.put("facet.field", "authoritativeMN");
        params.put("facet.limit", "-1");
        params.put("facet.mincount", "1");
        params.put("wt", "json");
        JsonNode response = solrQueryClient.query(params);
        List<String> result = new ArrayList<>();
        JsonNode values =
            response.path("facet_counts").path("facet_fields").path("authoritativeMN");
        if (values.isArray()) {
            for (int i = 0; i < values.size(); i += 2) {
                result.add(values.get(i).asText());
            }
        }
        log.debug("The list of the distinct authoritative mns is {}", result);
        return result;
    }

}
