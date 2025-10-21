package org.dataone.notifications.api.resource;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Enumerates the types of resources that can be accessed via the API -- i.e. the
 * {@code <resource-name>} element in the URL structure:
 * {@code http://<host-name>:<port>/<context-root>/<REST-uri>/<resource-name> }
 */
public enum ResourceType {
    datasetChanges, citations;
    //...add more resource names as needed...

    private static final String ALLOWED_VALUES = Arrays.stream(ResourceType.values())
        .map(Enum::name)
        .collect(Collectors.joining(", "));

    public static ResourceType fromString(String raw) {
        if (raw == null) {
            return null;
        }
        String candidate = normalize(raw);
        for (ResourceType rt : values()) {
            if (normalize(rt.name()).equals(candidate)) {
                return rt;
            }
        }
        throw new IllegalArgumentException(
            "Unknown resource type: '" + raw + "'. Allowed values: " + ALLOWED_VALUES);
    }

    private static String normalize(String s) {
        // case-insensitive, ignore hyphens/underscores/spaces and other non-alphanumerics
        return s.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }
}
