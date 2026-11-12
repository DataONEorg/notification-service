package org.dataone.notifications.event.notifier.channel;

import jakarta.enterprise.inject.Default;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

import org.dataone.notifications.event.NsEventMessage;
import org.dataone.notifications.event.notifier.NotificationListener;
import org.dataone.notifications.exception.NotificationDeliveryException;
import org.dataone.notifications.exception.UnsupportedResourceTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Default
public class EmailNotifier implements NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);
    Session session;

    /**
     * Constructor - Initializes the EmailNotifier with SMTP settings from environment variables.
     * Uses EMAIL_USERNAME and EMAIL_PASSWORD for authentication.
     */
    public EmailNotifier() {
        Properties props = new Properties();
        String emailHost = System.getenv("EMAIL_HOST");
        String emailPort = System.getenv("EMAIL_PORT");
        props.put("mail.smtp.host", emailHost);
        props.put("mail.smtp.port", emailPort);
        String emailUsername = System.getenv("EMAIL_USERNAME");
        String emailPassword = System.getenv("EMAIL_PASSWORD");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        String displayPasswd =
            emailPassword != null && !emailPassword.isBlank() ? "********" : "NOT SET!";
        log.debug(
            "Initializing EmailNotifier with SMTP host: {}, port: {}, "
                + "username: {}, and password: {}", emailHost, emailPort,
                emailUsername, displayPasswd);

        session = Session.getInstance(
            props, new jakarta.mail.Authenticator() {
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(
                        emailUsername, emailPassword);
                }
            });
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
            String emailText =
                """
                You subscribed to notifications for action: %s on the dataset with identifier: %s.
                A new version is available at: https://search-sandbox.test.dataone.org/view/%s
                """.formatted(resourceType, subscribedPid, newPid);

            // TODO: get subject from config/env
            message.setSubject("DataONE Event Notification");
            message.setText(emailText);
            Transport.send(message);
            log.debug(
                "Email sent to recipientEmail: {} for event: {}", recipientEmail, event);
        } catch (Exception e) {
            String errMsg = "Failed to send email to recipientEmail: " + recipientEmail
                + " for event: " + event + ", due to error: " + e.getMessage();
            log.error(errMsg);
            throw new NotificationDeliveryException(errMsg, e);
        }
        return recipientEmail.getAddress();
    }

    private InternetAddress getEmailForOrcid(String subscriber) throws AddressException {

        log.debug("Retrieving email for ORCID: {}", subscriber);

        // Placeholder implementation for retrieving email address based on subject (ORCID)
        // TODO: query the account service and/or ORCID API

        return new InternetAddress(
            subscriber.replaceAll("https?://", "").replace('/', '-') + "@example.com"); //
    }
}
