package org.dataone.notifications.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Defines the components of the JAX-RS application and supplies additional metadata. Given the URL
 * structure: {@code http://<host-name>:<port>/<context-root>/<REST-uri>/<resource-name>}, the
 * {@code <REST-uri>} element is defined by the {@code @ApplicationPath} annotation, below; e.g.
 * {@code @ApplicationPath("/api/v1")}. In our case, we set it to "/", since we don't want to
 * include the version in the URL.
 */
@ApplicationPath("/")
public class ApiConfigV1 extends Application {}
