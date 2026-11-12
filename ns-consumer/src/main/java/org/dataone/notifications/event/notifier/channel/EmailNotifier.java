package org.dataone.notifications.event.notifier.channel;

import jakarta.enterprise.inject.Default;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

import org.dataone.notifications.NsConfig;
import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.event.notifier.NotificationListener;
import org.dataone.notifications.exception.NotificationDeliveryException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Default
public class EmailNotifier implements NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);
    private static final Duration AS_HTTP_TIMEOUT_SEC = Duration.ofSeconds(
        NsConfig.getConfig().getInt("ns.messaging.accountSvc.connectTimeoutSec", 10));
    private static final HttpClient AS_HTTP_CLIENT =
        HttpClient.newBuilder().connectTimeout(AS_HTTP_TIMEOUT_SEC).build();
    private final String AS_URL;
    Session session;

    /**
     * Constructor - Initializes the EmailNotifier with SMTP settings from environment variables.
     * Uses EMAIL_USERNAME and EMAIL_PASSWORD for authentication.
     */
    public EmailNotifier() {
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

    private InternetAddress getEmailForOrcid(String subscriber)
        throws AddressException, IOException {

        // Remove any leading http:// or https:// from the subscriber string
        subscriber = subscriber.replaceAll("https?://", "");

        URI asUri = URI.create(AS_URL + subscriber);
        log.debug("Retrieving email for ORCID: {} using Account Svc GET: {}", subscriber, asUri);

        HttpRequest asRequest = HttpRequest.newBuilder(asUri)
            .timeout(AS_HTTP_TIMEOUT_SEC)
            .header("Accept", "application/json")
            .GET()
            .build();

        try {
            HttpResponse<String> response =
                AS_HTTP_CLIENT.send(asRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            log.debug("Response from Account Service: HTTP {}: {}", status, response.body());
            if (status < 200 || status >= 300) {
                throw new IOException(
                    "GET " + asUri + " failed with HTTP " + status + ": " + response.body());
            }
            return new InternetAddress(response.body().trim());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during GET " + asUri, e);
        }
    }
}
