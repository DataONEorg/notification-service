package org.dataone.notifications.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import org.dataone.notifications.NsConfig;

import java.net.URI;
import java.net.URL;

/**
 * A CDI producer that produces the URL for the authentication API.
 */
@ApplicationScoped
public class AuthApiUrlProducer {

    @Produces
    @Dependent
    @Default
    URL produceAuthApiUrl() throws Exception {
        String base = NsConfig.getConfig().getString("ns.auth.api.baseUrl");
        String path = NsConfig.getConfig().getString("ns.auth.api.authenticate");
        return URI.create(base + path).toURL();
    }
}
