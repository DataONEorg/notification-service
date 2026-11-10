package org.dataone.notifications.event;

import java.time.Instant;
import java.util.List;

public record LastSuccessfulRuntimes(List<Entry> entries) {

    public LastSuccessfulRuntimes {
        entries = List.copyOf(entries);
    }

    public record Entry(Instant timestamp, String nodeId) {}
}
