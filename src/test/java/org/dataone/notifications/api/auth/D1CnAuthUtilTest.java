package org.dataone.notifications.api.auth;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.apache.commons.configuration2.Configuration;
import org.dataone.notifications.NsConfig;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class D1CnAuthUtilTest {

    private static final String VALID_SUBJECT_INFO_XML = """
                                                         <subjectInfo>
                                                             <person>
                                                                 <subject>https://orcid.org/0000-1234-5678-999X</subject>
                                                                 <givenName>Jane</givenName>
                                                                 <familyName>Doe</familyName>
                                                                 <verified>true</verified>
                                                             </person>
                                                         </subjectInfo>
                                                         """;

    private static final String ERROR_XML = """
                                            <error>
                                                <description>The supplied authentication token (Session) could not be verified as being valid.</description>
                                            </error>
                                            """;

    private static final String EXPECTED_SUBJECT = "https://orcid.org/0000-1234-5678-999X";

    private static @NotNull Configuration getMockConfig() {
        var mockConfig = mock(Configuration.class);
        when(mockConfig.getString("ns.auth.api.baseUrl")).thenReturn("http://localhost");
        when(mockConfig.getString("ns.auth.api.authenticate")).thenReturn("/authenticate");
        return mockConfig;
    }

    @Test
    void getSubject_validTokenReturnsSubject() throws Exception {
        // Mock URL and HttpURLConnection
        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(200);
            InputStream xmlStream =
                new ByteArrayInputStream(VALID_SUBJECT_INFO_XML.getBytes(StandardCharsets.UTF_8));
            when(mockConn.getInputStream()).thenReturn(xmlStream);

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                String result = D1CnAuthUtil.getSubject("valid-token");

                assertEquals(EXPECTED_SUBJECT, result);
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_unauthorizedResponse() throws Exception {
        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(401);
            when(mockConn.getErrorStream()).thenReturn(
                new ByteArrayInputStream(ERROR_XML.getBytes(StandardCharsets.UTF_8)));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                NotAuthorizedException exception = assertThrows(
                    NotAuthorizedException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "invalid-token"));

                assertTrue(
                    exception.getMessage().contains("HTTP 401 Unauthorized"),
                    "Unexpected Exception Message (\"" + exception.getMessage() + "\")");
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_forbiddenResponse() throws Exception {
        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(403);
            when(mockConn.getErrorStream()).thenReturn(
                new ByteArrayInputStream(ERROR_XML.getBytes(StandardCharsets.UTF_8)));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                assertThrows(
                    NotAuthorizedException.class,
                    () -> D1CnAuthUtil.getSubject("forbidden-token"));

                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_serverErrorNoResponseStream() throws Exception {
        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(500);
            when(mockConn.getErrorStream()).thenReturn(null);

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                WebApplicationException exception = assertThrows(
                    WebApplicationException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(
                    exception.getMessage().contains("Authentication service returned no response"));
                assertEquals(503, exception.getResponse().getStatus());
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_authErrorNoResponseStream() throws Exception {
        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(401);
            when(mockConn.getErrorStream()).thenReturn(null);

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                NotAuthorizedException exception = assertThrows(
                    NotAuthorizedException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(exception.getMessage().contains("HTTP 401 Unauthorized"));
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_malformedXmlResponse() throws Exception {
        String malformedXml = "<subjectInfo><person><subject>incomplete";

        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(200);
            when(mockConn.getInputStream()).thenReturn(
                new ByteArrayInputStream(malformedXml.getBytes(StandardCharsets.UTF_8)));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                ProcessingException exception = assertThrows(
                    ProcessingException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(exception.getMessage().contains("Malformed XML response"));
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_malformedXmlWithAuthError() throws Exception {
        String malformedXml = "<error><description>bad token";

        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(401);
            when(mockConn.getErrorStream()).thenReturn(
                new ByteArrayInputStream(malformedXml.getBytes(StandardCharsets.UTF_8)));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                NotAuthorizedException exception = assertThrows(
                    NotAuthorizedException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(exception.getMessage().contains("HTTP 401 Unauthorized"));
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_noRootElement() throws Exception {
        String emptyXml = "";

        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(200);
            when(mockConn.getInputStream()).thenReturn(
                new ByteArrayInputStream(emptyXml.getBytes(StandardCharsets.UTF_8)));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                ProcessingException exception = assertThrows(
                    ProcessingException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(exception.getMessage().contains("Malformed XML response"));
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_noRootElementWithAuthError() throws Exception {
        String emptyXml = "";

        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(401);
            when(mockConn.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                NotAuthorizedException exception = assertThrows(
                    NotAuthorizedException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(
                    exception.getMessage().contains("HTTP 401 Unauthorized"),
                    exception.getMessage());
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_missingPersonElement() throws Exception {
        String xmlWithoutPerson = "<subjectInfo></subjectInfo>";

        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(200);
            when(mockConn.getInputStream()).thenReturn(
                new ByteArrayInputStream(xmlWithoutPerson.getBytes(StandardCharsets.UTF_8)));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                ProcessingException exception = assertThrows(
                    ProcessingException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(exception.getMessage()
                               .contains("Malformed subjectInfo response: missing person element"));
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_missingSubjectElement() throws Exception {
        String xmlWithoutSubject = """
                                   <subjectInfo>
                                       <person>
                                           <givenName>Jane</givenName>
                                           <familyName>Doe</familyName>
                                           <verified>true</verified>
                                       </person>
                                   </subjectInfo>
                                   """;

        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(200);
            when(mockConn.getInputStream()).thenReturn(
                new ByteArrayInputStream(xmlWithoutSubject.getBytes(StandardCharsets.UTF_8)));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                ProcessingException exception = assertThrows(
                    ProcessingException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(exception.getMessage().contains(
                    "Malformed subjectInfo response: missing or blank subject"));
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_blankSubject() throws Exception {
        String xmlWithBlankSubject = """
                                     <subjectInfo>
                                         <person>
                                             <subject>   </subject>
                                             <givenName>Jane</givenName>
                                             <familyName>Doe</familyName>
                                             <verified>true</verified>
                                         </person>
                                     </subjectInfo>
                                     """;

        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(200);
            when(mockConn.getInputStream()).thenReturn(
                new ByteArrayInputStream(xmlWithBlankSubject.getBytes(StandardCharsets.UTF_8)));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                ProcessingException exception = assertThrows(
                    ProcessingException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(exception.getMessage().contains(
                    "Malformed subjectInfo response: missing or blank subject"));
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_unexpectedRootElement() throws Exception {
        String xmlWithUnexpectedRoot = "<unexpected><content>data</content></unexpected>";

        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(200);
            when(mockConn.getInputStream()).thenReturn(
                new ByteArrayInputStream(xmlWithUnexpectedRoot.getBytes(StandardCharsets.UTF_8)));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                ProcessingException exception = assertThrows(
                    ProcessingException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(exception.getMessage().contains(
                    "Unexpected response format from authentication service: unexpected"));
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_unexpectedRootElementWithAuthError() throws Exception {
        String xmlWithUnexpectedRoot = "<unexpected><content>data</content></unexpected>";

        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(401);
            when(mockConn.getErrorStream()).thenReturn(
                new ByteArrayInputStream(xmlWithUnexpectedRoot.getBytes(StandardCharsets.UTF_8)));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                NotAuthorizedException exception = assertThrows(
                    NotAuthorizedException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(
                    exception.getMessage().contains("HTTP 401 Unauthorized"),
                    exception.getMessage());
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_connectionException() throws Exception {
        URL mockUrl = mock(URL.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenThrow(new ConnectException("Connection refused"));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                WebApplicationException exception = assertThrows(
                    WebApplicationException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(
                    exception.getMessage().contains("Unable to reach authentication service"));
                assertEquals(503, exception.getResponse().getStatus());
            }
        }
    }

    @Test
    void getSubject_socketTimeoutException() throws Exception {
        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenThrow(new SocketTimeoutException("Read timeout"));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                WebApplicationException exception = assertThrows(
                    WebApplicationException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(exception.getMessage().contains("Authentication service timeout"));
                assertEquals(503, exception.getResponse().getStatus());
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_genericException() throws Exception {
        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenThrow(new RuntimeException("Unexpected error"));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                WebApplicationException exception = assertThrows(
                    WebApplicationException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(exception.getMessage()
                               .contains("Authentication service error: Unexpected error"));
                assertEquals(503, exception.getResponse().getStatus());
                verify(mockConn).disconnect();
            }
        }
    }

    @Test
    void getSubject_errorWithoutDescription() throws Exception {
        String errorXmlWithoutDesc = "<error></error>";

        URL mockUrl = mock(URL.class);
        HttpURLConnection mockConn = mock(HttpURLConnection.class);

        try (MockedConstruction<URI> ignoredUri = mockConstruction(
            URI.class, (mockUri, context) -> {
                when(mockUri.toURL()).thenReturn(mockUrl);
            })) {

            when(mockUrl.openConnection()).thenReturn(mockConn);
            when(mockConn.getResponseCode()).thenReturn(401);
            when(mockConn.getErrorStream()).thenReturn(
                new ByteArrayInputStream(errorXmlWithoutDesc.getBytes(StandardCharsets.UTF_8)));

            try (MockedStatic<NsConfig> nsConfigMock = mockStatic(NsConfig.class)) {
                var mockConfig = getMockConfig();
                nsConfigMock.when(NsConfig::getConfig).thenReturn(mockConfig);

                NotAuthorizedException exception = assertThrows(
                    NotAuthorizedException.class,
                    () -> D1CnAuthUtil.getSubject(
                        "test-token"));

                assertTrue(exception.getMessage().contains("Unauthorized"));
                verify(mockConn).disconnect();
            }
        }
    }
}
