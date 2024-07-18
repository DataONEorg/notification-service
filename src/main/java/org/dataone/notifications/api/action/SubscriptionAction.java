package org.dataone.notifications.api.action;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.HttpServletRequest;
import org.dataone.notifications.api.ApiServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.logging.log4j.util.Strings.isBlank;

@ApplicationScoped
public class SubscriptionAction implements ApiAction {

    private static final String RESULT_SUBSCRIBED = """
                                                    {
                                                      result: subscribed;
                                                    }
                                                    """;

    private static final String RESULT_UNSUBSCRIBED = """
                                                      {
                                                        result: unsubscribed;
                                                      }
                                                      """;

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionAction.class);

    @Override
    public String processRequest(HttpServletRequest request, ApiServlet.HttpMethod httpMethod) {

        String subject = request.getParameter("subject");
        if (isBlank(subject)) {
            return errorResponse(request, httpMethod, subject, "no subject defined in request");
        }
        return switch (httpMethod) {
            case GET -> getSubscriptions(request, httpMethod, subject);
            case POST -> subscribe(request, httpMethod, subject);
            case PUT -> updateSubscription(request, httpMethod, subject);
            case DELETE -> unsubscribe(request, httpMethod, subject);
            default -> errorResponse(request, httpMethod, subject, "cannot respond to HTTP "
                + httpMethod + "request");
        };
    }

    public String subscribe(
        HttpServletRequest request, ApiServlet.HttpMethod httpMethod, String subject) {
        // TODO: handle error cases
        boolean success = true;
        String targetPid = request.getParameter("pid");
        LOGGER.debug("SubscriptionAction called with subject: {} and pid: {}", subject, targetPid);

        if (isBlank(targetPid)) {
            success = false;
        }

        // TODO: 1. Check this subject has read access to the targetPid
        //       2. subscribe in DB

        LOGGER.debug("success = {}; returning...", success);
        return (success) ? RESULT_SUBSCRIBED : RESULT_UNSUBSCRIBED;
    }

    private String updateSubscription(
        HttpServletRequest request, ApiServlet.HttpMethod httpMethod, String subject) {
        return errorResponse(request, httpMethod, subject, "cannot respond to HTTP "
            + httpMethod + "request");
    }

    private String getSubscriptions(
        HttpServletRequest request, ApiServlet.HttpMethod httpMethod, String subject) {
        return errorResponse(request, httpMethod, subject, "cannot respond to HTTP "
            + httpMethod + "request");
    }

    private String unsubscribe(
        HttpServletRequest request, ApiServlet.HttpMethod httpMethod, String subject) {
        return errorResponse(request, httpMethod, subject, "cannot respond to HTTP "
            + httpMethod + "request");
    }

    private String errorResponse(
        HttpServletRequest request, ApiServlet.HttpMethod httpMethod, String subject,
        String message) {
        return "{\n  result: \"error - " + message + "\";\n}";
    }
}
