package org.dataone.notifications.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

/**
 * Defines the components of the JAX-RS application and supplies additional metadata. Given the URL
 * structure: {@code http://<host-name>:<port>/<context-root>/<REST-uri>/<resource-name>}, the
 * {@code <REST-uri>} element is defined by the {@code @ApplicationPath} annotation, below; e.g.
 * {@code @ApplicationPath("/api/v1")}. In our case, we set it to "/", since we don't want to
 * include the version in the URL.
 */
@ApplicationPath("/v1")

@SecurityScheme(
    securitySchemeName = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
@OpenAPIDefinition(
    info = @Info(
        title = "DataONE Notifications API",
        version = "1.0.0",
        description = "API for managing DataONE notification subscriptions.",
        contact = @Contact(url = "https://www.dataone.org/contact/", name = "DataONE Support"),
        license = @License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0.html")
    ),
    // Apply bearer auth to all operations by default
    security = {
        @SecurityRequirement(name = "bearerAuth")
    }
)
public class ApiConfigV1 extends Application {}
