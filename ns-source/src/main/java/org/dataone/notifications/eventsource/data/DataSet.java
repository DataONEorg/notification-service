package org.dataone.notifications.eventsource.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.dataone.notifications.event.LastModificationTimes;
import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.eventsource.SubscriptionProcessor;
import org.dataone.notifications.eventsource.data.solr.SolrQueryClient;
import org.dataone.notifications.exception.IgnoredResourceTypeException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.dataone.notifications.resource.SubscriptionResourceType;

import org.dataone.notifications.util.HttpUtils;
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
import java.util.concurrent.TimeoutException;
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
    private static final String LAST_MODIFICATION_URL = "/lastModificationTime/datasetChanges";
    private static SubscriptionProcessor subscriptionProcessor;

    /**
     * Constructor
     */
    public DataSet() throws IOException, TimeoutException {
        subscriptionProcessor = SubscriptionProcessor.getInstance();
    }

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
        LastModificationTimes originalLatestModificationTimes =
            getRecordedMnLatestMap(nodeIdWithEarliestModificationFromSolr);
        if (originalLatestModificationTimes == null
            || originalLatestModificationTimes.runtimes() == null || originalLatestModificationTimes
            .runtimes().isEmpty()) {
            return;
        }
        // Build the MN/date conditions.
        List<String> mnQueries = new ArrayList<>();
        for (LastModificationTimes.Entry e : originalLatestModificationTimes.runtimes()) {
            long epochMillis = e.timestamp();   // milliseconds since the epoch (UTC)
            String timestamp = Instant.ofEpochMilli(epochMillis).toString();
            String nodeId = e.nodeId();
            mnQueries.add(
                "(authoritativeMN:\"" + escapeSolrValue(nodeId) + "\" " +
                    "AND dateModified:{" + timestamp + " TO *})"
            );

        }
        // Combine all MN/date conditions with OR.
        String mnDateQuery = "(" + String.join(" OR ", mnQueries) + ")";
        // Add the common conditions.
        String q = mnDateQuery +
            " AND obsoletedBy:[* TO *]" +
            " AND (formatType:\"RESOURCE\" OR formatType:\"METADATA\")";
        log.debug("The query is {}", q);
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
                if (guid == null || guid.isBlank()) {
                    log.warn("The guid can't be blank for processing. The record will be ignored.");
                    continue;
                }
                String newId = doc.path("obsoletedBy").asText(null);
                if (newId == null || newId.isBlank()) {
                    log.warn("The new guid can't be blank for processing. The record will be "
                                 + "ignored.");
                    continue;
                }
                String nodeId = doc.path("authoritativeMN").asText(null);
                if (nodeId == null || nodeId.isBlank()) {
                    log.warn(
                        "The node id can't be blank for processing. The record will be ignored.");
                    continue;
                }
                String modifiedDate = doc.path("dateModified").asText(null);
                String formatId = doc.path("formatId").asText(null);
                String formatType = doc.path("formatType").asText(null);
                log.debug(
                    "The id is {}, the new id is {} and the node id is {}", guid, newId, nodeId);
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
                    continue;
                }
                if (subscriptionResourceType != null) {
                    // The null value is subscriberId
                    NsEventMessage.NsEvent event =
                        new NsEventMessage.NsEvent(null, guid, newId, subscriptionResourceType);
                    NsEventMessage message = new NsEventMessage(event);
                    try {
                        subscriptionProcessor.process(message);
                    } catch (Exception e) {
                        log.warn("Couldn't process the guid {} since {}.", guid, e.getMessage());
                        continue;
                    }
                }
                // Since the modificationDate is desc, the first record has the newest value
                if (nodeId != null && modifiedDate != null) {
                    latestModifiedMap.putIfAbsent(nodeId, modifiedDate);
                }
            }
            // Solr returns the cursor to use for the next request.
            String nextCursorMark = result.path("nextCursorMark").asText();
            // No more results.
            if (nextCursorMark.equals(cursorMark)) {
                break;
            }
            cursorMark = nextCursorMark;
        }
        // Save the new LastModificationTimeEntries to the remote server
        List<LastModificationTimes.Entry> modifiedTimes =
            convertMapToLastModificationEntries(latestModifiedMap);
        saveLastModificationEntries(modifiedTimes);
    }

    private String escapeSolrValue(String value) {
        return ClientUtils.escapeQueryChars(value);
    }


    /**
     * This method will generate the LastModificationTimes for query. It covers those scenarios:
     * 1. A fresh start (no records from the remote database). It will use all node ids from
     * existing solr server and the current time as the last modification
     * 2. There are records from the remote database. It will compare the solr query result to
     * figure out if new node ids were added. If a new one is found, a node Id and the earliest
     * modification date will be added.
     * @param nodeIdWithEarliestModificationFromSolr  a map from solr which may contain the new
     *                                               node id
     * @return the LastModificationTimes combined the remote records and new added nodeId
     * @throws IOException
     */
    private LastModificationTimes getRecordedMnLatestMap(
        Map<String, String> nodeIdWithEarliestModificationFromSolr) throws IOException {
        boolean freshStart = false;
        ArrayList<String> originalNodeIds = new ArrayList<>();
        // Read the existing lastModificationTimes from the internal service
        LastModificationTimes result = HttpUtils.doGet(
               "/lastModificationTime/datasetChanges", LastModificationTimes.class);
        if (result == null || result.runtimes() == null || result.runtimes().isEmpty()) {
            log.debug("The LastModificationTimes from the remote server is empty.");
            freshStart = true;
        } else {
            for (LastModificationTimes.Entry e : result.runtimes()) {
                String nodeId = e.nodeId();
                if (nodeId != null && nodeId.isBlank()) {
                    log.debug("Add node id {} into the original node list ", nodeId);
                    originalNodeIds.add(nodeId);
                }
            }
        }
        // Add new node IDs
        long currentTime = System.currentTimeMillis();
        // Handle the scenarios of a fresh start or a new node id was found in the solr doc.
        List<LastModificationTimes.Entry> entries = new ArrayList<>();
        for (String nodeId : nodeIdWithEarliestModificationFromSolr.keySet()) {
            if (nodeId == null || nodeId.isBlank()) {
                log.debug("The node id is null or blank, skip it");
                continue;
            }
            if (freshStart) {
                log.debug(
                    "This a fresh start (no recorded file or database). Node id {} and "
                        + "current time {} will be put into LastModificationTimes.", nodeId,
                    currentTime);
                entries.add(new LastModificationTimes.Entry(currentTime, nodeId));
            } else {
                if (!originalNodeIds.contains(nodeId)) {
                    String earliestModficationTime =
                        nodeIdWithEarliestModificationFromSolr.get(nodeId);
                    long epochMillis = Instant.parse(earliestModficationTime).toEpochMilli();
                    log.debug(
                        "Found a new node id (it is not in the remote server). Node "
                            + "id {} and the earliest modification {} will be put into map.",
                        nodeId, earliestModficationTime);
                    result.addEntry(epochMillis, nodeId);
                }
            }
        }
        // Save the LastModificationTimes if it is a fresh start
        if (freshStart) {
            saveLastModificationEntries(entries);
            result = new LastModificationTimes(entries);
        }
        return result;
    }

    private List<LastModificationTimes.Entry> convertMapToLastModificationEntries(
        Map<String, String> map) throws IOException {
        if (map == null || map.isEmpty()) {
            return null;
        }
        List<LastModificationTimes.Entry> entries = new ArrayList<>();
        for (String nodeId : map.keySet()) {
            if (nodeId != null && !nodeId.isBlank())  {
                String timestamp = map.get(nodeId);
                long epochMillis = Instant.parse(timestamp).toEpochMilli();
                entries.add(new LastModificationTimes.Entry(epochMillis, nodeId));
            }
        }
        if (!entries.isEmpty()) {
            return entries;
        } else {
            return null;
        }
    }

    private void saveLastModificationEntries(List<LastModificationTimes.Entry> entries) throws IOException {
        if (entries != null && !entries.isEmpty()) {
            HttpUtils.doPut(LAST_MODIFICATION_URL, entries);
        } else {
            log.debug("An empty ModificationTimes.Entry list will not be saved to the remote "
                          + "server");
        }
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
