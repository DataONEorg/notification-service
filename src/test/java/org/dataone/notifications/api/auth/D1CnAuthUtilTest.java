package org.dataone.notifications.api.auth;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class D1CnAuthUtilTest {
    private static final String VALID_SUBJECT_INFO_XML =
        """
        <ns2:subjectInfo xmlns:ns2="http://ns.dataone.org/service/types/v1">
            <person>
                <subject>https://orcid.org/0000-1234-5678-999X</subject>
                <givenName>Jane</givenName>
                <familyName>Doe</familyName>
                <verified>true</verified>
            </person>
        </ns2:subjectInfo>
        """;
    private static final String ERROR_XML =
        """
        <error>
            <description>The supplied authentication token (Session) could not be verified as being valid.</description>
        </error>
        """;
    private static final String MOCK_URL = "http://localhost/authenticate";
    private static final String EXPECTED_SUBJECT = "https://orcid.org/0000-1234-5678-999X";
    private static final String MALFORMED_XML = "<subjectInfo><person><subject>incomplete";
    private static final String XML_WITHOUT_PERSON = "<subjectInfo></subjectInfo>";
    private static final String XML_WITHOUT_SUBJECT =
        """
        <subjectInfo>
            <person>
                <givenName>Jane</givenName>
                <familyName>Doe</familyName>
                <verified>true</verified>
            </person>
        </subjectInfo>
        """;
    private static final String XML_WITH_BLANK_SUBJECT =
        """
        <subjectInfo>
            <person>
                <subject>   </subject>
                <givenName>Jane</givenName>
                <familyName>Doe</familyName>
                <verified>true</verified>
            </person>
        </subjectInfo>
        """;
    private static final String TLS13_POST_HANDSHAKE_ERR_RESPONSE =
        """
        <!DOCTYPE HTML PUBLIC "-//IETF//DTD HTML 2.0//EN">
        <html><head>
            <title>403 Forbidden</title>
        </head><body>
            <h1>Forbidden</h1>
            <p>You don't have permission to access this resource.Reason: Cannot perform Post-Handshake Authentication.<br /></p>
        </body></html>
        """;
    private static final String XML_WITH_UNEXPECTED_ROOT =
        "<unexpected><content>data</content></unexpected>";
    private static final String ERROR_XML_WITHOUT_DESC = "<error></error>";

    private static @NotNull URL getMockUrl(HttpURLConnection mockConn) throws IOException {
        URL mockUrl = mock(URL.class);
        when(mockUrl.toString()).thenReturn(MOCK_URL);
        when(mockUrl.openConnection()).thenReturn(mockConn);
        return mockUrl;
    }

    private static @NotNull HttpURLConnection getMockConnection(
        int httpResponseCode, String mockResponseXml) throws IOException {

        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(httpResponseCode);
        InputStream xmlStream =
            new ByteArrayInputStream(mockResponseXml.getBytes(StandardCharsets.UTF_8));
        if (httpResponseCode < 401) {
            when(mockConn.getInputStream()).thenReturn(xmlStream);
        } else {
            when(mockConn.getErrorStream()).thenReturn(xmlStream);
        }
        return mockConn;
    }

    @Test
    void getSubject_validTokenReturnsSubject() throws Exception {
        HttpURLConnection mockConn = getMockConnection(200, VALID_SUBJECT_INFO_XML);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        String result = util.getSubject("valid-token");

        assertEquals(EXPECTED_SUBJECT, result);
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_unauthorizedResponse() throws Exception {

        HttpURLConnection mockConn = getMockConnection(401, ERROR_XML);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        NotAuthorizedException exception =
            assertThrows(NotAuthorizedException.class, () -> util.getSubject("invalid-token"));

        assertTrue(
            exception.getMessage().contains("HTTP 401 Unauthorized"),
            "Unexpected Exception Message (\"" + exception.getMessage() + "\")");
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_forbiddenResponse() throws Exception {

        HttpURLConnection mockConn = getMockConnection(403, ERROR_XML);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        assertThrows(NotAuthorizedException.class, () -> util.getSubject("forbidden-token"));

        verify(mockConn).disconnect();

    }

    @Test
    void getSubject_serverErrorNoResponseStream() throws Exception {

        HttpURLConnection mockConn = getMockConnection(500, "");
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        WebApplicationException exception =
            assertThrows(WebApplicationException.class, () -> util.getSubject("test-token"));

        assertTrue(exception.getMessage().contains("Authentication service returned no response"));
        assertEquals(503, exception.getResponse().getStatus());
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_authErrorNoResponseStream() throws Exception {

        HttpURLConnection mockConn = getMockConnection(401, "");
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        NotAuthorizedException exception =
            assertThrows(NotAuthorizedException.class, () -> util.getSubject("test-token"));

        assertTrue(exception.getMessage().contains("HTTP 401 Unauthorized"));
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_malformedXmlResponse() throws Exception {

        HttpURLConnection mockConn = getMockConnection(200, MALFORMED_XML);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        ProcessingException exception =
            assertThrows(ProcessingException.class, () -> util.getSubject("test-token"));

        assertTrue(
            exception.getMessage().contains("Failed to parse authentication service response"));
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_malformedXmlWithAuthError() throws Exception {

        HttpURLConnection mockConn = getMockConnection(401, MALFORMED_XML);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);


        NotAuthorizedException exception =
            assertThrows(NotAuthorizedException.class, () -> util.getSubject("test-token"));

        assertTrue(exception.getMessage().contains("HTTP 401 Unauthorized"));
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_tls13PostHandshakeAuthError() throws Exception {

        HttpURLConnection mockConn = getMockConnection(403, TLS13_POST_HANDSHAKE_ERR_RESPONSE);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);


        NotAuthorizedException exception =
            assertThrows(NotAuthorizedException.class, () -> util.getSubject("test-token"));

        assertTrue(exception.getMessage().contains("HTTP 401 Unauthorized"),
                   "Unexpected exception message: " + exception.getMessage());
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_noRootElement() throws Exception {

        HttpURLConnection mockConn = getMockConnection(200, "");
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        WebApplicationException exception =
            assertThrows(WebApplicationException.class, () -> util.getSubject("test-token"));

        assertTrue(
            exception.getMessage().contains("Authentication service returned no response"),
            "Unexpected exception message: [" + exception.getMessage() + "]");
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_noRootElementWithAuthError() throws Exception {

        HttpURLConnection mockConn = getMockConnection(401, "");
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        NotAuthorizedException exception =
            assertThrows(NotAuthorizedException.class, () -> util.getSubject("test-token"));

        assertTrue(
            exception.getMessage().contains("HTTP 401 Unauthorized"),
            exception.getMessage());
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_missingPersonElement() throws Exception {


        HttpURLConnection mockConn = getMockConnection(200, XML_WITHOUT_PERSON);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        ProcessingException exception =
            assertThrows(ProcessingException.class, () -> util.getSubject("test-token"));

        assertTrue(exception.getMessage()
                       .contains("Malformed subjectInfo response: missing person element"));
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_missingSubjectElement() throws Exception {

        HttpURLConnection mockConn = getMockConnection(200, XML_WITHOUT_SUBJECT);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        ProcessingException exception =
            assertThrows(ProcessingException.class, () -> util.getSubject("test-token"));

        assertTrue(exception.getMessage()
                       .contains("Malformed subjectInfo response: missing or blank subject"));
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_blankSubject() throws Exception {

        HttpURLConnection mockConn = getMockConnection(200, XML_WITH_BLANK_SUBJECT);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        ProcessingException exception =
            assertThrows(ProcessingException.class, () -> util.getSubject("test-token"));

        assertTrue(exception.getMessage()
                       .contains("Malformed subjectInfo response: missing or blank subject"));
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_unexpectedRootElement() throws Exception {

        HttpURLConnection mockConn = getMockConnection(200, XML_WITH_UNEXPECTED_ROOT);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        ProcessingException exception =
            assertThrows(ProcessingException.class, () -> util.getSubject("test-token"));

        assertTrue(exception.getMessage().contains(
            "Unexpected response format from authentication service: unexpected"));
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_unexpectedRootElementWithAuthError() throws Exception {
        HttpURLConnection mockConn = getMockConnection(401, XML_WITH_UNEXPECTED_ROOT);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        NotAuthorizedException exception =
            assertThrows(NotAuthorizedException.class, () -> util.getSubject("test-token"));

        assertTrue(
            exception.getMessage().contains("HTTP 401 Unauthorized"),
            exception.getMessage());
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_connectionException() throws Exception {
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        when(mockUrl.openConnection()).thenThrow(new ConnectException("Connection refused"));

        WebApplicationException exception =
            assertThrows(WebApplicationException.class, () -> util.getSubject("test-token"));

        assertTrue(exception.getMessage().contains("Unable to reach authentication service"));
        assertEquals(503, exception.getResponse().getStatus());
    }

    @Test
    void getSubject_socketTimeoutException() throws Exception {
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        when(mockConn.getResponseCode()).thenThrow(new SocketTimeoutException("Read timeout"));

        WebApplicationException exception =
            assertThrows(WebApplicationException.class, () -> util.getSubject("test-token"));

        assertTrue(exception.getMessage().contains("Authentication service timeout"));
        assertEquals(503, exception.getResponse().getStatus());
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_genericException() throws Exception {
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        when(mockConn.getResponseCode()).thenThrow(new RuntimeException("Unexpected error"));

        WebApplicationException exception =
            assertThrows(WebApplicationException.class, () -> util.getSubject("test-token"));

        assertTrue(
            exception.getMessage().contains("Authentication service error: Unexpected error"));
        assertEquals(503, exception.getResponse().getStatus());
        verify(mockConn).disconnect();
    }

    @Test
    void getSubject_errorWithoutDescription() throws Exception {
        HttpURLConnection mockConn = getMockConnection(401, ERROR_XML_WITHOUT_DESC);
        URL mockUrl = getMockUrl(mockConn);
        D1CnAuthUtil util = new D1CnAuthUtil(mockUrl);

        NotAuthorizedException exception =
            assertThrows(NotAuthorizedException.class, () -> util.getSubject("test-token"));

        assertTrue(exception.getMessage().contains("Unauthorized"));
        verify(mockConn).disconnect();
    }
}
