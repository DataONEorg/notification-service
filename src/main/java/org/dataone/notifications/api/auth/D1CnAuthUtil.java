package org.dataone.notifications.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class D1CnAuthUtil {

    private static final Logger logger = LoggerFactory.getLogger(D1CnAuthUtil.class);

    private URL authApiUrl = null;

    /**
     * Default constructor required for CDI proxying.
     */
    D1CnAuthUtil() {}

    @Inject
    public D1CnAuthUtil(URL authApiUrl) {
        this.authApiUrl = authApiUrl;
    }

    /**
     * Retrieves subject information based on the provided authentication token.
     *
     * @param token the auth token to be used for verifying and retrieving user information.
     * @return the subject string from the authentication response
     * @throws NotAuthorizedException  if the authentication fails (401/403) or token is invalid
     * @throws WebApplicationException if there are service connectivity or server issues
     * @throws ProcessingException     if the response format is invalid or unparseable
     */
    public String getSubject(String token) throws NotAuthorizedException {

        String urlStr = authApiUrl.toString();
        logger.debug("Authentication API URL: {}", urlStr);

        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) authApiUrl.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer " + token.trim());
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);

            int responseCode = con.getResponseCode();
            logger.debug("Authentication API response code: {}", responseCode);
            // a 401 Unauthorized response should be used for missing or bad authentication, and
            // a 403 Forbidden response should be used when the user is authenticated but isn’t
            // authorized to perform the requested operation on the given resource.
            boolean authError = (responseCode == 401 || responseCode == 403);

            try (InputStream responseStream = (responseCode >= 200 && responseCode < 400)
                                              ? con.getInputStream() : con.getErrorStream()) {
                if (responseStream == null) {
                    logger.error(
                        "No response received from authentication API. HTTP code: {}",
                        responseCode);
                    if (authError) {
                        throw new NotAuthorizedException("Bearer");
                    } else {
                        throw new WebApplicationException(
                            "Authentication service returned no response",
                            Response.Status.SERVICE_UNAVAILABLE);
                    }
                }

                byte[] responseBytes = responseStream.readAllBytes();
                String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
                logger.debug("Authentication API response body: {}", responseBody);

                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                dbFactory.setNamespaceAware(true);
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc;
                try (ByteArrayInputStream xmlStream = new ByteArrayInputStream(responseBytes)) {
                    doc = dBuilder.parse(xmlStream);
                } catch (Exception xmlEx) {
                    if (authError) {
                        logger.debug(
                            "Authentication failed, response not parseable as XML. Error: {}; "
                                + "response: {}",
                            xmlEx.getMessage(), responseBody);
                        throw new NotAuthorizedException("Bearer");
                    }
                    logger.error(
                        "Failed to parse authentication service response: {}", responseBody, xmlEx);
                    throw new ProcessingException(
                        "Failed to parse authentication service response: " + responseBody, xmlEx);
                }
                Element root = doc.getDocumentElement();
                if (root == null) {
                    logger.error("Authentication API response XML has no root element.");
                    if (authError) {
                        throw new NotAuthorizedException("Bearer");
                    }
                    throw new ProcessingException(
                        "Authentication service returned XML with no root element");
                }

                // Process subjectInfo response (successful authentication)
                if (root.getTagName() != null && root.getTagName().endsWith("subjectInfo")) {
                    Element person = (Element) root.getElementsByTagName("person").item(0);
                    if (person == null) {
                        logger.error("No <person> element found in authentication response.");
                        throw new ProcessingException(
                            "Malformed subjectInfo response: missing person element");
                    }
                    String subject = getElementText(person, "subject");

                    if (subject == null || subject.isBlank()) {
                        logger.error("Subject missing in authentication subjectInfo response.");
                        throw new ProcessingException(
                            "Malformed subjectInfo response: missing or blank subject");
                    }
                    return subject;

                } else if ("error".equals(root.getTagName())) {
                    // This is an authentication error response
                    String errorDesc = getElementText(root, "description");
                    logger.debug("Authentication API returned error: {}", errorDesc);
                    throw new NotAuthorizedException("Bearer");

                } else {
                    // Unexpected response format
                    logger.error(
                        "Unexpected root element in authentication response: {}",
                        root.getTagName());
                    if (authError) {
                        throw new NotAuthorizedException("Bearer");
                    }
                    throw new ProcessingException(
                        "Unexpected response format from authentication service: "
                            + root.getTagName());
                }
            }
        } catch (java.net.ConnectException ce) {
            logger.error("Failed to connect to authentication server", ce);
            throw new WebApplicationException(
                "Unable to reach authentication service", ce,
                                              Response.Status.SERVICE_UNAVAILABLE);
        } catch (java.net.SocketTimeoutException te) {
            logger.error("Timeout while connecting to authentication server", te);
            throw new WebApplicationException(
                "Authentication service timeout", te,
                                              Response.Status.SERVICE_UNAVAILABLE);
        } catch (NotAuthorizedException e) {
            // Re-throw authentication exceptions as-is
            throw e;
        } catch (WebApplicationException | ProcessingException e) {
            // Re-throw JAX-RS exceptions as-is
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during authentication request: {}", e.getMessage(), e);
            throw new WebApplicationException(
                "Authentication service error: " + e.getMessage(), e,
                                              Response.Status.SERVICE_UNAVAILABLE);
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    private static String getElementText(Element parent, String tagName) {
        Element elem = (Element) parent.getElementsByTagName(tagName).item(0);
        return (elem != null) ? elem.getTextContent() : null;
    }
}
