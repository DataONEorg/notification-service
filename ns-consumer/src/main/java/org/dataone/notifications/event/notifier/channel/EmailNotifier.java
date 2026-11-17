package org.dataone.notifications.event.notifier.channel;

import jakarta.enterprise.inject.Default;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.dataone.notifications.NsConfig;
import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.event.notifier.NotificationListener;
import org.dataone.notifications.exception.NotificationDeliveryException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Default
public class EmailNotifier implements NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);
    private static final Duration AS_HTTP_TIMEOUT_SEC = Duration.ofSeconds(
        NsConfig.getConfig().getInt("ns.messaging.accountSvc.connectTimeoutSec", 10));
    private static final HttpClient AS_HTTP_CLIENT =
        HttpClient.newBuilder().connectTimeout(AS_HTTP_TIMEOUT_SEC).build();

    // Thread-safe once configured; only used to create DocumentBuilders
    private static final DocumentBuilderFactory DOC_BUILDER_FACTORY = newSecureDbf();

    // DocumentBuilder is NOT thread-safe, so keep one per thread and reuse it
    private static final ThreadLocal<DocumentBuilder> DOC_BUILDER =
        ThreadLocal.withInitial(() -> {
            try {
                return DOC_BUILDER_FACTORY.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new IllegalStateException("Unable to create DocumentBuilder", e);
            }
        });
    private final String AS_URL;
    private final HttpClient accountServiceClient;
    Session session;

    /**
     * Constructor - Initializes the EmailNotifier with SMTP settings from environment variables.
     * Uses EMAIL_USERNAME and EMAIL_PASSWORD for authentication.
     */
    public EmailNotifier() {
        this(AS_HTTP_CLIENT);
    }

    EmailNotifier(HttpClient accountServiceClient) {
        this.accountServiceClient = accountServiceClient;
        String emailHost = NsConfig.getConfig().getString("ns.messaging.email.host");
        String emailPort = NsConfig.getConfig().getString("ns.messaging.email.port", "1025");
        String emailUsername = System.getenv("EMAIL_USERNAME");
        String emailPassword = System.getenv("EMAIL_PASSWORD");

        Properties sessionProps = new Properties();
        sessionProps.put("mail.smtp.host", emailHost);
        sessionProps.put("mail.smtp.port", emailPort);
        sessionProps.put("mail.smtp.auth", "true");
        sessionProps.put("mail.smtp.starttls.enable", "true");
        String displayPasswd =
            emailPassword != null && !emailPassword.isBlank() ? "********" : "NOT SET!";
        log.debug(
            "Initializing EmailNotifier with SMTP host: {}, port: {}, "
                + "username: {}, and password: {}", emailHost, emailPort, emailUsername,
            displayPasswd);

        session = Session.getInstance(
            sessionProps, new jakarta.mail.Authenticator() {
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(emailUsername, emailPassword);
                }
            });

        //URL for retrieving email addresses from the account service
        AS_URL = NsConfig.getConfig().getString("ns.messaging.accountSvc.url");
    }

    /**
     * Extract the first email address of the person whose subject matches the given ORCID, from a
     * DataONE subjectInfo XML document, e.g.:
     * <pre>{@code
     * <ns2:subjectInfo xmlns:ns2="http://ns.dataone.org/service/types/v1">
     *   <person><subject>http://orcid.org/0000-...</subject>
     *     <email>someone@example.com</email>...</person>
     * </ns2:subjectInfo>
     * }</pre>
     *
     * @param xml   the subjectInfo XML
     * @param orcid the ORCID to match; any leading http:// or https:// is ignored when comparing
     */
    static String extractEmail(String xml, String orcid) throws IOException {
        String orcidNoScheme = stripScheme(orcid);
        try {
            DocumentBuilder builder = DOC_BUILDER.get();
            builder.reset();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            NodeList persons = doc.getElementsByTagName("person");
            for (int i = 0; i < persons.getLength(); i++) {
                Element person = (Element) persons.item(i);
                NodeList subjects = person.getElementsByTagName("subject");
                if (subjects.getLength() == 0 || !stripScheme(
                    subjects.item(0).getTextContent()).equals(orcidNoScheme)) {
                    continue;
                }
                NodeList emails = person.getElementsByTagName("email");
                String email = emails.getLength() == 0 ? "" : emails.item(0).getTextContent().trim();
                if (email.isEmpty()) {
                    throw new IOException(
                        "No email found for " + orcid + " in Account Service response: " + xml);
                }
                return email;
            }
            throw new IOException(
                "No person matching " + orcid + " in Account Service response: " + xml);
        } catch (SAXException e) {
            throw new IOException("Unable to parse Account Service response: " + xml, e);
        }
    }

    private static DocumentBuilderFactory newSecureDbf() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // Harden against XXE
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            return dbf;
        } catch (ParserConfigurationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Remove any leading http:// or https://
    static String stripScheme(String subject) {
        subject = subject.trim();
        if (subject.startsWith("https://")) {
            return subject.substring(8);
        }
        if (subject.startsWith("http://")) {
            return subject.substring(7);
        }
        return subject;
    }

    @Override
    public String handleNotification(NsEventMessage.NsEvent event)
        throws UnsupportedResourceTypeException, NotificationDeliveryException {

        InternetAddress recipientEmail = null;
        try {
            MimeMessage message = new MimeMessage(session);

            recipientEmail = getEmailForOrcid(event.subscriberId());
            String resourceType = event.SubscriptionResourceType().name();
            String subscribedPid = event.subscribedPid();
            String newPid = event.newPid();

            // TODO: get "from:" address from config/env
            message.setFrom(new InternetAddress("noreply@myapp.test", "My App"));

            message.setRecipient(Message.RecipientType.TO, recipientEmail);

            // TODO: get body, specific to resourceType, from config/env
            String emailText = """
                               You subscribed to notifications for action: %s on the dataset with identifier: %s.
                               A new version is available at: https://search-sandbox.test.dataone.org/view/%s
                               """.formatted(resourceType, subscribedPid, newPid);

            // TODO: get subject from config/env
            message.setSubject("DataONE Event Notification");
            message.setText(emailText);
            Transport.send(message);
            log.debug("Email sent to recipientEmail: {} for event: {}", recipientEmail, event);
        } catch (Exception e) {
            String errMsg =
                "Failed to send email to recipientEmail: " + recipientEmail + " for event: " + event
                    + ", due to error: " + e.getMessage();
            log.error(errMsg);
            throw new NotificationDeliveryException(errMsg, e);
        }
        return recipientEmail.getAddress();
    }

    InternetAddress getEmailForOrcid(String subscriber)
        throws AddressException, IOException {

        // url-encode the subscriber string. URLEncoder uses form encoding, so need to fix spaces
        // ("+"), although ORCIDs shouldn't have spaces anyway
        String encodedSubscriber =
            URLEncoder.encode(subscriber, StandardCharsets.UTF_8).replace("+", "%20");

        URI acctSvcUrl =
            URI.create((AS_URL.endsWith("/") ? AS_URL : AS_URL + "/") + encodedSubscriber);
        log.debug(
            "Retrieving email for ORCID: {} using Account Svc GET: {}", subscriber, acctSvcUrl);

        HttpRequest acctSvcRequest = HttpRequest.newBuilder(acctSvcUrl).timeout(AS_HTTP_TIMEOUT_SEC)
            .header("Accept", "application/xml").GET().build();

        try {
            HttpResponse<String> response =
                accountServiceClient.send(acctSvcRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            log.debug("Response from Account Service: HTTP {}: {}", status, response.body());
            if (status < 200 || status >= 300) {
                throw new IOException(
                    "GET " + acctSvcUrl + " failed with HTTP " + status + ": " + response.body());
            }
            return new InternetAddress(extractEmail(response.body(), subscriber));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during GET " + acctSvcUrl, e);
        }
    }
}
