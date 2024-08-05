package org.dataone.notifications.api.resource;

/**
 * Enumerates the types of resources that can be accessed via the API -- i.e. the
 * <code>&lt;resource-config&gt;</code> element in the URL structure:
 * {@literal
 *   http://<host-name>:<port>/<context-root>/<REST-uri>/<resource-name>
 * }
 */
public enum ResourceType {
    DATASETS("datasets"),
    CITATIONS("citations");
    //...add more resource names as needed...

    ResourceType(String value) {}
}
