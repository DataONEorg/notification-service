package org.dataone.notifications.api;

import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dataone.notifications.api.action.ApiAction;
import org.dataone.notifications.api.action.SubscriptionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.logging.log4j.util.Strings.isBlank;

@WebServlet(name = "API Servlet", urlPatterns = {"/v1/*"})
public class ApiServlet extends HttpServlet {

    private static final String URI_BASE = "/v1/";
    private static final Pattern RESOURCE_PATTERN = Pattern.compile(URI_BASE + "(.+)([/?])");
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiServlet.class);

    private final Map<String, ApiAction> actionsMap = new HashMap<>();

    @Inject
    private SubscriptionAction registrationService;

    /**
     * Initialize the actions that will be used to respond to api requests. You can include any
     * number of arbitrary actions in the actionsMap HashMap, provided each one implements the
     * ApiAction interface. These can be injected using the @Inject annotation
     */
    @Override
    public void init() {
        actionsMap.put("dataset", new SubscriptionAction());
        actionsMap.put("citation", new SubscriptionAction());
        if (LOGGER.isDebugEnabled()) {
            StringBuilder initMsg = new StringBuilder(
                "ApiServlet initialized with the following available services:\n");
            for (String serviceName : actionsMap.keySet()) {
                initMsg.append(serviceName);
            }
            LOGGER.debug(initMsg.toString());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        processQuery(request, response, HttpMethod.DELETE);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        processQuery(request, response, HttpMethod.GET);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        processQuery(request, response, HttpMethod.POST);
    }

    private void processQuery(
        HttpServletRequest request, HttpServletResponse response, HttpMethod httpMethod)
        throws IOException {
        LOGGER.debug("HTTP {} request received", httpMethod);
        String resourceType = extractResourceType(request.getRequestURI());
        if (!isBlank(resourceType)) {
            if (actionsMap.containsKey(resourceType)) {
                ApiAction action = actionsMap.get(resourceType);
                String result = action.processRequest(request, httpMethod);
                LOGGER.debug("returning result: {}", result);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(result);
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                LOGGER.debug("API endpoint ({}) not found", resourceType);
                response.getWriter().write("API endpoint not found");
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            LOGGER.debug("resource type was blank in request URI: {}", request.getRequestURI());
            response.getWriter().write("Invalid request URI: " + request.getRequestURI());
        }
    }

    private String extractResourceType(String urlString) {
        URI uri;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            LOGGER.warn("URISyntaxException trying to get URI from {}", urlString, e);
            return "";
        }
        String path = uri.getPath();
        Matcher matcher = RESOURCE_PATTERN.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        }
        LOGGER.debug("No match for regex {} in path {}", RESOURCE_PATTERN, path);
        return "";
    }

    public enum HttpMethod {
        DELETE, GET, POST, PUT
    }
}
