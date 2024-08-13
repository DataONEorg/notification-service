package org.dataone.notifications.api.resource;

import java.util.Locale;

/**
 * Enumerates the types of resources that can be accessed via the API -- i.e. the
 * <code>&lt;resource-config&gt;</code> element in the URL structure:
 * {@code
 *   http://<host-name>:<port>/<context-root>/<REST-uri>/<resource-name>
 * }
 */
public enum ResourceType {
    datasets,
    citations;
    //...add more resource names as needed...
}
