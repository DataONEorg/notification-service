package org.dataone.notifications;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailUtil {
    Session session;
    private static final Logger log = LoggerFactory.getLogger(EmailUtil.class);
    
    public EmailUtil(){
        Properties props = new Properties();
        boolean emailAuthEnabled = Boolean.parseBoolean(System.getenv("EMAIL_AUTH_ENABLED"));
        String emailHost = System.getenv("EMAIL_HOST");
        String emailPort = System.getenv("EMAIL_PORT");
        log.debug("Initializing EmailUtil with host: {}, port: {}, auth enabled: {}", emailHost, emailPort, emailAuthEnabled);
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

    public String sendMail(String resourceType, String pid){
        try{
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("noreply@myapp.test", "My App"));
            message.setRecipient(
                    Message.RecipientType.TO,
                    new InternetAddress("test@example.com")
            );
            String emailText = """
                    This is a test email. You have subscribed to notifications for
                    action: %s  on the record with pid: %s. 
                    """.formatted(resourceType, pid);
            message.setSubject("Hello from the Notification Service!");
            message.setText(emailText);

            // Send it
            Transport.send(message);

            System.out.println("Email sent successfully!");
            return "Email sent successfully!";
        }catch(Exception e){
            e.printStackTrace();
            return "Failed to send email: " + e.getMessage();
        }
        
    }
}
