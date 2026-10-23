package org.dataone.notifications;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailNotifier implements NotificationListener {
    Session session;
    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    public EmailNotifier(){
        Properties props = new Properties();
        boolean emailAuthEnabled = Boolean.parseBoolean(System.getenv("EMAIL_AUTH_ENABLED"));
        String emailHost = System.getenv("EMAIL_HOST");
        String emailPort = System.getenv("EMAIL_PORT");
        log.debug("Initializing EmailNotifier with host: {}, port: {}, auth enabled: {}", emailHost, emailPort, emailAuthEnabled);
        props.put("mail.smtp.host", emailHost);
        props.put("mail.smtp.port", emailPort);
        if(emailAuthEnabled) {
            log.debug("Email authentication is enabled. Using credentials from environment variables.");
            String emailUsername = System.getenv("EMAIL_USERNAME");
            String emailPassword = System.getenv("EMAIL_PASSWORD");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            session = Session.getInstance(props, new jakarta.mail.Authenticator() {
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(emailUsername, emailPassword);
                }
            });
        } else{
            log.debug("Email authentication is disabled. No credentials will be used.");
            props.put("mail.smtp.auth", "false");
            props.put("mail.smtp.starttls.enable", "false");

            session = Session.getInstance(props);
        }
    }

    @Override
    public String handleNotification(String subscriber, String resourceType, String pid){
        try{
            MimeMessage message = new MimeMessage(session);

            InternetAddress recipientEmail = getEmailForOrcid(subscriber);
            // TODO: get from address from config/env
            message.setFrom(new InternetAddress("noreply@myapp.test", "My App"));
            message.setRecipient(Message.RecipientType.TO, recipientEmail);
            // TODO: get body from config/env
            String emailText = """
                    This is a test email. You have subscribed to notifications for
                    action: %s  on the record with pid: %s.
                    """.formatted(resourceType, pid);
            // TODO: get subject from config/env
            message.setSubject("Hello from the Notification Service!");
            message.setText(emailText);

            Transport.send(message);

            log.debug("Email sent successfully!");
            return "Email sent successfully!";
        }catch(Exception e){
            log.error("Failed to send email, due to error: {}", e.getMessage(), e);
            return "Failed to send email, due to error: " + e.getMessage();
        }

    }

    private InternetAddress getEmailForOrcid(String subscriber) throws AddressException {
        // Placeholder implementation for retrieving email address based on subject (ORCID)
        // In a real implementation, this would query the account service and/or ORCID API
        log.debug("Retrieving email for ORCID: {}", subscriber);

        return new InternetAddress(
            subscriber.replaceAll("https?//:", "").replace('/', '-') + "@example.com"); //
    }
}
