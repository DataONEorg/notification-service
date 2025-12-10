package org.dataone.notifications.api.resource;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Enumerates the types of resources that can be accessed via the API -- i.e. the
 * {@code <resource-name>} element in the URL structure:
 * {@code http://<host-name>:<port>/<context-root>/<REST-uri>/<resource-name> }
 */
public enum SubscriptionResourceType {
    datasetChanges, citations;
    //...add more resource names as needed...

    private static final String ALLOWED_VALUES = Arrays.stream(SubscriptionResourceType.values())
        .map(Enum::name)
        .collect(Collectors.joining(", "));

    public static SubscriptionResourceType fromString(String raw) {
        if (raw == null) {
            return null;
        }
        String candidate = normalize(raw);
        for (SubscriptionResourceType rt : values()) {
            if (normalize(rt.name()).equals(candidate)) {
                return rt;
            }
        }
        throw new IllegalArgumentException(
            "Unknown SubscriptionResourceType: '" + raw + "'. Allowed values: " + ALLOWED_VALUES);
    }

    private static String normalize(String s) {
        // case-insensitive, ignore hyphens/underscores/spaces and other non-alphanumerics
        return s.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }
}
