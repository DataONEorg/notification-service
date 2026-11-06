package org.dataone.notifications.eventsource.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.eventsource.data.solr.SolrQueryClient;
import org.dataone.notifications.exception.IgnoredResourceTypeException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.dataone.notifications.resource.SubscriptionResourceType;

import org.dataone.notifications.util.StringUtils;
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
import java.util.regex.Pattern;

/**
 * The class to poll the solr server and find the candidate objects for the notification service
 */
public class DataSet {
    private static final Pattern PORTAL_FORMAT_ID_PATTERN =
        Pattern.compile("https://purl\\.dataone\\.org/portals-.*");
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
        Map<String, String> nodeIdWithEarliestModificationFromSolr =
            getMNEarliestModificationMap();
        if (nodeIdWithEarliestModificationFromSolr.isEmpty()) {
            log.warn(
                "The notification service cannot find any nodeIds in the solr service {}. So it "
                    + "will stop polling.", solrQueryUrl);
            return;
        }
        Map<String, String> originalMnLatestModifiedDateMap =
            getRecordedMnLatestMap(nodeIdWithEarliestModificationFromSolr);
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
            params.put(
                "fl",
                "id,authoritativeMN,obsoletedBy,dateModified,formatId,formatType,resourceMap");
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
                String formatId = doc.path("formatId").asText(null);
                String formatType = doc.path("formatType").asText(null);
                List<String> resourceMaps = new ArrayList<>();
                JsonNode resourceMapNode = doc.path("resourceMap");
                if (resourceMapNode.isArray()) {
                    for (JsonNode node : resourceMapNode) {
                        resourceMaps.add(node.asText());
                    }
                }
                SubscriptionResourceType subscriptionResourceType = null;
                try {
                    subscriptionResourceType = getResourceType(formatType, formatId, resourceMaps);
                } catch (Exception e) {
                    log.warn("Can't get the subscription resource type for {} since {}, and this "
                                 + "object is ignored.", guid, e.getMessage());
                }
                if (subscriptionResourceType != null) {
                    NsEventMessage message = new NsEventMessage(null);
                }
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


    /**
     * Read the recorded map from the file/database if the file/database exist. The map has a key
     * of node id and the value of the latest modification date. It also handles the scenarios of
     * fresh start (no record file or database) or a new node id found in the solr docs.
     * @param nodeIdWithEarliestModificationFromSolr  a map from solr which may contain the new
     *                                               node id
     * @return the map with nodeId/latestModficationDate
     * @throws IOException
     */
    private Map<String, String> getRecordedMnLatestMap(
        Map<String, String> nodeIdWithEarliestModificationFromSolr) throws IOException {
        boolean freshStart = false;
        File file = new File(MN_LATEST_MODIFICATION_FILE_PATH);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> mnLatestMap = new HashMap<>();
        // Read the existing map if the file exists.
        if (file.exists()) {
            mnLatestMap = mapper.readValue(file,
                                           new TypeReference<Map<String, String>>() {});
        } else {
            freshStart = true;
        }
        // Add new node IDs
        String currentTime = Instant.now().toString();
        boolean updated = false;
        // Handle the scenarios of a fresh start or a new node id was found in the solr doc.
        for (String nodeId : nodeIdWithEarliestModificationFromSolr.keySet()) {
            if (nodeId == null || nodeId.isBlank()) {
                log.debug("The node id is null or blank, skip it");
                continue;
            }
            if (freshStart) {
                log.debug("This a fresh start (no recorded file or database). Node id {} and "
                              + "current time {} will be put into map.", nodeId, currentTime);
                mnLatestMap.put(nodeId, currentTime);
                updated = true;
            } else {
                if (!mnLatestMap.containsKey(nodeId)) {
                    String earliestModficationTime =
                        nodeIdWithEarliestModificationFromSolr.get(nodeId);
                    log.debug(
                        "Found a new node id (it is not in the recorded file or database). Node "
                            + "id {} and the earliest modification {} will be put into map.",
                        nodeId, earliestModficationTime);
                    mnLatestMap.put(nodeId, earliestModficationTime);
                    updated = true;
                }
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
     * Get a map, whose key is a distinct mn id and the value is the earliest modification date,
     * from solr
     * @return an empty map if it finds nothing
     * @throws IOException
     * @throws InterruptedException
     */
    protected Map<String, String> getMNEarliestModificationMap()
        throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("q", "*:*");
        params.put("rows", "0");
        params.put("json.facet",
                   "{mns:{type:terms,field:authoritativeMN,limit:-1," +
                       "facet:{earliestDateModified:'min(dateModified)'}}}");
        params.put("wt", "json");
        JsonNode response = solrQueryClient.query(params);
        Map<String, String> result = new HashMap<>();
        JsonNode buckets = response.path("facets").path("mns").path("buckets");
        if (buckets.isArray()) {
            for (JsonNode bucket : buckets) {
                String mnId = bucket.path("val").asText();
                String earliestDateModified =
                    bucket.path("earliestDateModified").asText();
                result.put(mnId, earliestDateModified);
            }
        }
        log.debug("The distinct authoritative MNs and their earliest " +
                      "dateModified values are {}", result);
        return result;
    }

    /// Extracts the SubscriptionResourceType from the given parameters.
    ///
    /// `formatType` can be `RESOURCE`, `DATA`, or `METADATA`:
    /// - `DATA` can be ignored because there will always be another event for the corresponding
    ///   resourceMap update, and we don't want to alert twice for the same thing.
    /// - `RESOURCE` is a resourceMap, which is always a dataset, so return
    ///   `SubscriptionResourceType.datasetChanges`.
    /// - For `METADATA`:
    ///   - If `formatId` matches `https://purl.dataone.org/portals-.*`, return
    ///     `SubscriptionResourceType.portalMetaChanges` (portals do not have a resourceMap).
    ///   - If a `resourceMap` field exists, ignore the event because there will be another event
    ///     for the corresponding resourceMap update, and we don't want to alert twice for the same
    ///     thing.
    ///   - If a `resourceMap` field does not exist, this is a science-metadata-only dataset; return
    ///     `SubscriptionResourceType.datasetChanges`.
    ///
    /// References:
    ///
    /// - List of available formats: [DataONE formats](https://cn.dataone.org/cn/v2/formats)
    /// - Enumeration of available resource types:
    /// [org.dataone.notifications.api.resource.SubscriptionResourceType]
    ///
    /// @param formatType  the format type of the data object, it can be DATA, METADATA, or RESOURCE
    /// @param formatId  the format id of the data object
    /// @param resourceMap  the list of resourceMap aggregates the object
    /// @return The resource type as a SubscriptionResourceType
    /// @throws IgnoredResourceTypeException     If the resource type is ignored (e.g., "DATA")
    /// @throws UnsupportedResourceTypeException If the resource type cannot be determined.
    ///
    protected static SubscriptionResourceType getResourceType(
        String formatType, String formatId, List<String> resourceMap)
        throws IgnoredResourceTypeException, UnsupportedResourceTypeException {
        if (StringUtils.isBlank(formatType)) {
            throw new IllegalArgumentException("FormatType parameter cannot be blank.");
        }
        if (StringUtils.isBlank(formatId)) {
            throw new IllegalArgumentException("FormatId parameter cannot be blank.");
        }
        return switch (formatType) {
            case "DATA" -> throw new IgnoredResourceTypeException("formatType: DATA is ignored");
            case "RESOURCE" -> SubscriptionResourceType.datasetChanges;
            case "METADATA" -> {
                if (PORTAL_FORMAT_ID_PATTERN.matcher(formatId).matches()) {
                    log.debug("formatId: {} matches portal pattern -> portalMetaChanges", formatId);
                    yield SubscriptionResourceType.portalMetaChanges;
                }
                if (resourceMap != null && !resourceMap.isEmpty() && !StringUtils.isBlank(
                    resourceMap.getFirst())) {
                    log.debug("resourceMap: {} exists -> ignore to avoid duplicate", resourceMap);
                    throw new IgnoredResourceTypeException(
                        "formatType: METADATA with resourceMap is ignored");
                }
                yield SubscriptionResourceType.datasetChanges;
            }
            default ->
                throw new UnsupportedResourceTypeException("Unsupported formatType: " + formatType);
        };
    }

}
