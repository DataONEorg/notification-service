package org.dataone.notifications.eventsource.data;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.dataone.notifications.NsConfig;
import org.dataone.notifications.event.LastModificationTimes;
import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.eventsource.EventSource;
import org.dataone.notifications.eventsource.SubscriptionProcessor;
import org.dataone.notifications.eventsource.data.solr.SolrQueryClient;
import org.dataone.notifications.exception.IgnoredResourceTypeException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.dataone.notifications.util.HttpUtils;
import org.dataone.notifications.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * The class to poll the solr server and find the candidate objects for the notification service
 */
public class DataSet implements EventSource, AutoCloseable {
    private static final Pattern PORTAL_FORMAT_ID_PATTERN =
        Pattern.compile("https://purl\\.dataone\\.org/portals-.*");
    private static final Logger log = LoggerFactory.getLogger(DataSet.class);
    private static final String LAST_MODIFICATION_URL = "/lastModificationTime/datasetChanges";
    private final String solrQueryUrl;
    private final SolrQueryClient solrQueryClient;
    private final SubscriptionProcessor subscriptionProcessor;

    /**
     * Constructor
     */
    public DataSet() throws IOException, TimeoutException {
        solrQueryUrl =
            NsConfig.getConfig().getString("ns.source.eventsources.dataset.solrQueryUrl");
        if (solrQueryUrl == null || solrQueryUrl.isBlank()) {
            throw new IllegalArgumentException(
                "ns.source.eventsources.dataset.solrQueryUrl is required");
        }
        solrQueryClient = new SolrQueryClient(solrQueryUrl);
        subscriptionProcessor = SubscriptionProcessor.getInstance();
    }

    public static void main(String[] args)
        throws IOException, InterruptedException, TimeoutException {
        try (DataSet dataSet = new DataSet()) {
            dataSet.poll();
        }
    }

    /// Extracts the SubscriptionResourceType from the given parameters.
    ///
    /// `formatType` can be `RESOURCE`, `DATA`, or `METADATA`:
    /// - `DATA` can be ignored because there will always be another event for the corresponding
    /// resourceMap update, and we don't want to alert twice for the same thing.
    /// - `RESOURCE` is a resourceMap, which is always a dataset, so return
    /// `SubscriptionResourceType.datasetChanges`.
    /// - For `METADATA`:
    ///   - If `formatId` matches `https://purl.dataone.org/portals-.*`, return
    /// `SubscriptionResourceType.portalMetaChanges` (portals do not have a resourceMap).
    ///   - If a `resourceMap` field exists, ignore the event because there will be another event
    /// for the corresponding resourceMap update, and we don't want to alert twice for the same
    /// thing.
    ///   - If a `resourceMap` field does not exist, this is a science-metadata-only dataset; return
    /// `SubscriptionResourceType.datasetChanges`.
    ///
    /// References:
    ///
    /// - List of available formats: [DataONE formats](https://cn.dataone.org/cn/v2/formats)
    /// - Enumeration of available resource types: {@link SubscriptionResourceType}
    ///
    /// @param formatType  the format type of the data object, it can be DATA, METADATA, or RESOURCE
    /// @param formatId    the format id of the data object
    /// @param resourceMap the list of resourceMap aggregates for the object
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
        log.debug(
            "Determine SubscriptionResourceType for formatType: {}, formatId: {}, resourceMap: {}",
            formatType, formatId, resourceMap);
        return switch (formatType) {
            case "DATA" -> throw new IgnoredResourceTypeException(
                "formatType: DATA is ignored; there should be a separate event for the "
                    + "corresponding resourceMap update (formatType: RESOURCE);");
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
                        "formatType: METADATA with resourceMap is ignored; there should be a "
                            + "separate event for the corresponding resourceMap update "
                            + "(formatType: RESOURCE)");
                }
                yield SubscriptionResourceType.datasetChanges;
            }
            default ->
                throw new UnsupportedResourceTypeException("Unsupported formatType: " + formatType);
        };
    }

    @Override
    public void close() throws IOException {
        subscriptionProcessor.close();
    }

    /**
     * Poll the solr server and send the candidate objects to the notification queue
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public void poll() throws IOException, InterruptedException {

        // Ask Solr which node IDs exist, and their earliest ever modification dates
        Map<String, String> earliestSolrModTimes = getEarliestSolrMnModTimes();
        if (earliestSolrModTimes.isEmpty()) {
            log.warn(
                "The DataSet event source can't find any nodeIds in Solr ({}), so it will exit",
                solrQueryUrl);
            return;
        }

        // Read saved per-node last mod times from the internal API. Add any nodes that are found in
        // earliestSolrModTimes, but which do not yet exist in the NS API DB
        LastModificationTimes lastModTimesForQuery = getLastModTimesForQuery(earliestSolrModTimes);
        if (lastModTimesForQuery == null || lastModTimesForQuery.entries() == null
            || lastModTimesForQuery.entries().isEmpty()) {
            return;
        }
        // Build the MN/date conditions.
        List<String> mnQueries = new ArrayList<>();
        for (LastModificationTimes.Entry e : lastModTimesForQuery.entries()) {
            long epochMillis = e.timestamp();   // milliseconds since the epoch (UTC)
            String timestamp = Instant.ofEpochMilli(epochMillis).toString();
            String nodeId = e.nodeId();
            mnQueries.add(
                "(authoritativeMN:\"" + escapeSolrValue(nodeId) + "\" " + "AND dateModified:{"
                    + timestamp + " TO *})");

        }
        // Combine all MN/date conditions with OR.
        String mnDateQuery = "(" + String.join(" OR ", mnQueries) + ")";
        // Add the common conditions.
        String q = mnDateQuery + " AND obsoletedBy:[* TO *]"
            + " AND (formatType:\"RESOURCE\" OR formatType:\"METADATA\")";
        log.debug("The query is {}", q);
        String cursorMark = "*";
        // A new list of the latest modification dates for this poll
        List<LastModificationTimes.Entry> lastModTimeUpdates = new ArrayList<>();
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
                    log.warn(
                        "The new guid can't be blank for processing. The record will be ignored.");
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
                List<String> resourceMaps = new ArrayList<>();
                JsonNode resourceMapNode = doc.path("resourceMap");
                if (resourceMapNode.isArray()) {
                    for (JsonNode node : resourceMapNode) {
                        resourceMaps.add(node.asText());
                    }
                }
                log.debug(
                    "oldPid {}; newPid {}; nodeId {}; formatType {}; formatId {}; resourceMaps {}",
                    guid, newId, nodeId, formatType, formatId, resourceMaps);
                SubscriptionResourceType subscriptionResourceType;
                try {
                    subscriptionResourceType = getResourceType(formatType, formatId, resourceMaps);
                } catch (Exception e) {
                    log.warn(
                        "Can't get the subscription resource type for {} since: {}. This object "
                            + "will be ignored.", guid, e.getMessage());
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
                    long epochMillis = Instant.parse(modifiedDate).toEpochMilli();
                    lastModTimeUpdates.add(new LastModificationTimes.Entry(epochMillis, nodeId));
                    log.debug(
                        "New modification date {} for node id {} added to lastModTimeUpdates",
                        modifiedDate, nodeId);
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
        if (lastModTimeUpdates.isEmpty()) {
            log.debug("No new LastModificationTimes found in Solr; nothing to save via NS API");
        } else {
            log.debug(
                "Saving {} new LastModificationTimes entries (from Solr) via NS API: {}",
                lastModTimeUpdates.size(), lastModTimeUpdates);
            saveLastModificationEntries(lastModTimeUpdates);
        }
    }

    private String escapeSolrValue(String value) {
        return ClientUtils.escapeQueryChars(value);
    }

    /**
     * This method will generate the LastModificationTimes for query. It covers these scenarios: 1.
     * A fresh start (no records from the NS API database). It will use all node ids from existing
     * solr server and the current time as the last modification 2. There are records from the NS
     * API database (not a fresh start). It will compare the solr query result to figure out if new
     * node ids were added. If a new one is found, a node id and the current time as the last
     * modification date will be added.
     *
     * @param earliestSolrModTimes solr results map which may contain new nodeIds
     * @return the LastModificationTimes combined the remote records and new added nodeIds
     * @throws IOException if it can't read the LastModificationTimes from the NS API server
     */
    private LastModificationTimes getLastModTimesForQuery(
        Map<String, String> earliestSolrModTimes) throws IOException {

        LastModificationTimes lastModTimesFromDb =
            HttpUtils.doGet("/lastModificationTime/datasetChanges", LastModificationTimes.class);
        long currentTime = System.currentTimeMillis();
        if (lastModTimesFromDb == null || lastModTimesFromDb.entries() == null || lastModTimesFromDb
            .entries().isEmpty()) {
            log.debug(
                "The list of Last Modification Times from the NS API DB is empty. Adding entries "
                    + "from Solr.");
            List<LastModificationTimes.Entry> entries = new ArrayList<>();
            if (earliestSolrModTimes.isEmpty()) {
                log.warn(
                    "The list of earliest modification times from Solr is empty. Returning empty "
                        + "LastModificationTimes record");
                return new LastModificationTimes(entries);
            }
            for (String nodeId : earliestSolrModTimes.keySet()) {
                if (nodeId != null && !nodeId.isBlank()) {
                    log.debug(
                        "Adding entry from Solr for nodeId: {}; time: {}", nodeId,
                        currentTime);
                    entries.add(new LastModificationTimes.Entry(currentTime, nodeId));
                }
            }
            LastModificationTimes returnModTimes = new LastModificationTimes(entries);
            log.debug("Saving new LastModificationTimes entries via API: {}", returnModTimes);
            saveLastModificationEntries(entries);
            return returnModTimes;
        }

        // Keep a set for efficient checks against the node IDs already stored in the API.
        Set<String> recordedNodeIds = new HashSet<>();
        for (LastModificationTimes.Entry entry : lastModTimesFromDb.entries()) {
            if (entry.nodeId() != null && !entry.nodeId().isBlank()) {
                recordedNodeIds.add(entry.nodeId());
            }
        }
        for (String nodeId : earliestSolrModTimes.keySet()) {
            if (nodeId != null && !nodeId.isBlank() && recordedNodeIds.add(nodeId)) {
                log.debug(
                    "Adding new node id {} that was in Solr, but not in the NS API DB. Mod time is "
                        + "{}", nodeId, currentTime);
                lastModTimesFromDb = lastModTimesFromDb.addEntry(currentTime, nodeId);
            }
        }
        return lastModTimesFromDb;
    }

    private void saveLastModificationEntries(List<LastModificationTimes.Entry> entries)
        throws IOException {
        if (entries != null && !entries.isEmpty()) {
            HttpUtils.doPut(LAST_MODIFICATION_URL, entries);
        } else {
            log.debug("ModificationTimes.Entry list is empty; not saving via api");
        }
    }

    /**
     * Get a map, whose key is a distinct mn id and the value is the earliest modification date,
     * from solr
     *
     * @return an empty map if it finds nothing
     * @throws IOException
     * @throws InterruptedException
     */
    protected Map<String, String> getEarliestSolrMnModTimes()
        throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("q", "*:*");
        params.put("rows", "0");
        params.put(
            "json.facet", "{mns:{type:terms,field:authoritativeMN,limit:-1,"
                + "facet:{earliestDateModified:'min(dateModified)'}}}");
        params.put("wt", "json");
        JsonNode response = solrQueryClient.query(params);
        Map<String, String> result = new HashMap<>();
        JsonNode buckets = response.path("facets").path("mns").path("buckets");
        if (buckets.isArray()) {
            for (JsonNode bucket : buckets) {
                String mnId = bucket.path("val").asText();
                String earliestDateModified = bucket.path("earliestDateModified").asText();
                result.put(mnId, earliestDateModified);
            }
        }
        log.debug(
            "The distinct authoritative MNs and their earliest dateModified values are {}",
            result);
        return result;
    }

}
