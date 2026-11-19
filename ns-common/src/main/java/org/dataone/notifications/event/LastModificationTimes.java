package org.dataone.notifications.event;

import java.util.List;

public record LastModificationTimes(List<Entry> runtimes) {

    public LastModificationTimes {
        runtimes = List.copyOf(runtimes);
    }

    /**
     * @param timestamp the last modification time, in milliseconds since the epoch (UTC). A
     *                  boxed Long so that a missing JSON value is null, rather than 0
     * @param nodeId    the DataONE node identifier, e.g. "urn:node:KNB"
     */
    public record Entry(Long timestamp, String nodeId) {}
}
