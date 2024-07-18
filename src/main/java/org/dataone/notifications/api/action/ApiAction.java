package org.dataone.notifications.api.action;

import jakarta.servlet.http.HttpServletRequest;
import org.dataone.notifications.api.ApiServlet;

/**
 * Interface shared by any arbitrary actions we want to call from the api servlet
 */
public interface ApiAction {
    String processRequest(HttpServletRequest request, ApiServlet.HttpMethod httpMethod);
}
