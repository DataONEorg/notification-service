package org.dataone.notifications.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Defines the components of the JAX-RS application and supplies additional meta-data.
 * Given the URL structure: {@literal
 * http://<host-name>:<port>/<context-root>/<REST-uri>/<resource-name>}, the {@literal <REST-uri>}
 * element is defined by the <code>@ApplicationPath</code> annotation, below; e.g.
 * <code>@ApplicationPath("/api/v1")</code>. In our case, we set it to "/", since we don't want to
 * include the version in the URL.
 */
@ApplicationPath("/")
public class ApiConfigV1 extends Application {}
