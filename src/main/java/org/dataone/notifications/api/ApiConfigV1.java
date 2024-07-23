package org.dataone.notifications.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Defines the components of the JAX-RS application and supplies additional meta-data.
 * Given the URL structure:
 * {@literal
 *  http://<host-name>:<port>/<context-root>/<REST-config>/<resource-config>
 * }
 * the {@literal <REST-config>} element is defined by the <code>@ApplicationPath</code> annotation;
 * e.g. <code>@ApplicationPath("/api/v1")</code>
 */
@ApplicationPath("/api/v1")
public class ApiConfigV1 extends Application {}
