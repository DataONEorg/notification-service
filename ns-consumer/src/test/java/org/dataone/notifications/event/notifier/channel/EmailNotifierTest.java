package org.dataone.notifications.event.notifier.channel;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.configuration2.Configuration;
import org.dataone.notifications.NsConfig;
import org.dataone.notifications.event.NsEventMessage.NsEvent;
import org.dataone.notifications.exception.NotificationDeliveryException;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotifierTest {

    private static final String ORCID = "https://orcid.org/0000-0001-2345-6789";
    private static final String EMAIL = "person@example.org";
    private static final String ACCOUNT_SERVICE_URL = "https://accounts.example.org/subjects";

    @Mock
    private HttpClient client;
    @Mock
    private HttpResponse<String> response;
    @Mock
    private Configuration config;

    private EmailNotifier notifier;

    @BeforeEach
    void setUp() {
        lenient().when(config.getInt("ns.messaging.accountSvc.connectTimeoutSec", 10))
            .thenReturn(10);
        when(config.getString("ns.messaging.email.host")).thenReturn("smtp.example.org");
        when(config.getString("ns.messaging.email.port", "1025")).thenReturn("1025");
        when(config.getString("ns.messaging.accountSvc.url")).thenReturn(ACCOUNT_SERVICE_URL);
        try (MockedStatic<NsConfig> nsConfig = mockStatic(NsConfig.class)) {
            nsConfig.when(NsConfig::getConfig).thenReturn(config);
            notifier = new EmailNotifier(client);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "' http://orcid.org/0000-0123-4567-890X ', orcid.org/0000-0123-4567-890X",
        "' https://orcid.org/0000-0123-4567-890X ', orcid.org/0000-0123-4567-890X",
        "' orcid.org/0000-0123-4567-890X ', orcid.org/0000-0123-4567-890X",
        "'ftp://orcid.org/0000-0123-4567-890X', ftp://orcid.org/0000-0123-4567-890X",
        "'HTTP://orcid.org/0000-0123-4567-890X', HTTP://orcid.org/0000-0123-4567-890X"
    })
    void stripSchemeRemovesOnlyLeadingHttpSchemes(String subject, String expected) {
        assertEquals(expected, EmailNotifier.stripScheme(subject));
    }

    @Test
    void extractEmailFindsMatchingPersonAndTrimsFirstEmail() throws Exception {
        String xml = """
            <subjectInfo>
              <person><email>no-subject@example.org</email></person>
              <person><subject>https://orcid.org/other</subject><email>other@example.org</email></person>
              <person><subject> http://orcid.org/0000-0001-2345-6789 </subject>
                <email> person@example.org </email><email>second@example.org</email>
              </person>
            </subjectInfo>
            """;

        assertEquals(EMAIL, EmailNotifier.extractEmail(xml, " " + ORCID + " "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "<email>   </email>"})
    void extractEmailRejectsMissingOrBlankEmail(String emailElement) {
        String xml = "<subjectInfo><person><subject>" + ORCID + "</subject>"
            + emailElement + "</person></subjectInfo>";

        IOException error = assertThrows(IOException.class,
            () -> EmailNotifier.extractEmail(xml, ORCID));
        assertTrue(error.getMessage().contains("No email found for " + ORCID));
    }

    @Test
    void extractEmailRejectsUnmatchedPerson() {
        String xml = "<subjectInfo><person><subject>" + ORCID
            + "-different</subject><email>" + EMAIL + "</email></person></subjectInfo>";

        IOException error = assertThrows(IOException.class,
            () -> EmailNotifier.extractEmail(xml, ORCID));
        assertTrue(error.getMessage().contains("No person matching " + ORCID));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<subjectInfo>",
        "<!DOCTYPE subjectInfo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>"
            + "<subjectInfo>&xxe;</subjectInfo>"
    })
    void extractEmailRejectsMalformedXmlAndDoctype(String xml) {
        IOException error = assertThrows(IOException.class,
            () -> EmailNotifier.extractEmail(xml, ORCID));
        assertTrue(error.getMessage().contains("Unable to parse Account Service response"));
        assertInstanceOf(org.xml.sax.SAXException.class, error.getCause());
    }

    @Test
    void getEmailForOrcidEncodesSubscriberAndReturnsAddress() throws Exception {
        String subscriber = ORCID + " a+b";
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(subjectInfo(subscriber, EMAIL));

        assertEquals(EMAIL, notifier.getEmailForOrcid(subscriber).getAddress());

        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(URI.create(ACCOUNT_SERVICE_URL
            + "/https%3A%2F%2Forcid.org%2F0000-0001-2345-6789%20a%2Bb"),
            request.getValue().uri());
        assertEquals("GET", request.getValue().method());
        assertEquals("application/xml", request.getValue().headers().firstValue("Accept").orElseThrow());
        assertTrue(request.getValue().timeout().isPresent());
    }

    @Test
    void getEmailForOrcidRejectsUnsuccessfulResponse() throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(response);
        when(response.statusCode()).thenReturn(503);
        when(response.body()).thenReturn("unavailable");

        IOException error = assertThrows(IOException.class, () -> notifier.getEmailForOrcid(ORCID));
        assertTrue(error.getMessage().contains("HTTP 503: unavailable"));
    }

    @Test
    void getEmailForOrcidPropagatesIoFailure() throws Exception {
        IOException cause = new IOException("connection failed");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(cause);

        assertSame(cause, assertThrows(IOException.class, () -> notifier.getEmailForOrcid(ORCID)));
    }

    @Test
    void getEmailForOrcidRestoresInterruptedStatus() throws Exception {
        InterruptedException cause = new InterruptedException("interrupted");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(cause);

        try {
            IOException error = assertThrows(IOException.class, () -> notifier.getEmailForOrcid(ORCID));
            assertSame(cause, error.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void handleNotificationSendsMessageAndReturnsRecipient() throws Exception {
        EmailNotifier spy = spy(notifier);
        doReturn(new InternetAddress(EMAIL)).when(spy).getEmailForOrcid(ORCID);
        NsEvent event = event();

        try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
            assertEquals(EMAIL, spy.handleNotification(event));

            ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
            transport.verify(() -> Transport.send(message.capture()));
            MimeMessage sent = (MimeMessage) message.getValue();
            assertEquals(EMAIL, ((InternetAddress) sent.getRecipients(Message.RecipientType.TO)[0]).getAddress());
            assertEquals("noreply@myapp.test", ((InternetAddress) sent.getFrom()[0]).getAddress());
            assertEquals("DataONE Event Notification", sent.getSubject());
            assertTrue(sent.getContent().toString().contains("datasetChanges"));
            assertTrue(sent.getContent().toString().contains("subscribed-pid"));
            assertTrue(sent.getContent().toString().contains("new-pid"));
            verify(spy).getEmailForOrcid(ORCID);
        }
    }

    @Test
    void handleNotificationWrapsLookupFailureWithoutSending() throws Exception {
        EmailNotifier spy = spy(notifier);
        IOException cause = new IOException("lookup failed");
        doThrow(cause).when(spy).getEmailForOrcid(ORCID);

        try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
            NotificationDeliveryException error = assertThrows(NotificationDeliveryException.class,
                () -> spy.handleNotification(event()));
            assertSame(cause, error.getCause());
            transport.verifyNoInteractions();
        }
    }

    @Test
    void handleNotificationWrapsTransportFailure() throws Exception {
        EmailNotifier spy = spy(notifier);
        doReturn(new InternetAddress(EMAIL)).when(spy).getEmailForOrcid(ORCID);
        MessagingException cause = new MessagingException("send failed");

        try (MockedStatic<Transport> transport = mockStatic(Transport.class)) {
            transport.when(() -> Transport.send(any(Message.class))).thenThrow(cause);
            NotificationDeliveryException error = assertThrows(NotificationDeliveryException.class,
                () -> spy.handleNotification(event()));
            assertSame(cause, error.getCause());
            assertTrue(error.getMessage().contains(EMAIL));
        }
    }

    private static NsEvent event() {
        return new NsEvent(ORCID, "subscribed-pid", "new-pid",
            SubscriptionResourceType.datasetChanges);
    }

    private static String subjectInfo(String subject, String email) {
        return "<subjectInfo><person><subject>" + subject + "</subject><email>" + email
            + "</email></person></subjectInfo>";
    }
}
