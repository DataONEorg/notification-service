package org.dataone.notifications.event;

import java.util.ArrayList;
import java.util.List;

public record LastModificationTimes(List<Entry> entries) {

    public LastModificationTimes {
        entries = List.copyOf(entries);
    }

    /**
     * Add a pair of timestamp/nodeId into the record
     * @param timestamp  the timestamp will be added
     * @param nodeId  the nodeId will be added
     * @return a new LastModificationTimes record
     */
    public LastModificationTimes addEntry(Long timestamp, String nodeId) {
        List<Entry> updated = new ArrayList<>(entries);
        updated.add(new Entry(timestamp, nodeId));
        return new LastModificationTimes(updated);
    }

    /**
     * @param timestamp the last modification time, in milliseconds since the epoch (UTC). A
     *                  boxed Long so that a missing JSON value is null, rather than 0
     * @param nodeId    the DataONE node identifier, e.g. "urn:node:KNB"
     */
    public record Entry(Long timestamp, String nodeId) {}
}
