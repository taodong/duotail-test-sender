package com.duotail.utils.email.sender;

import jakarta.mail.Authenticator;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Transport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Properties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
public class GmailSendService {
    private final Properties properties = new Properties();

    private final JavaMailSender javaMailSender;
    private final GmailProperties gmailProperties;

    public GmailSendService(JavaMailSender javaMailSender, GmailProperties gmailProperties) {
        this.javaMailSender = javaMailSender;
        this.gmailProperties = gmailProperties;
        this.properties.put("mail.smtp.auth", "true");
        this.properties.put("mail.smtp.starttls.enable", "true");
        this.properties.put("mail.smtp.host", gmailProperties.getHost());
        this.properties.put("mail.smtp.port", gmailProperties.getPort());
    }

    void sendEmail(GmailRequest emailRequest) throws MessagingException {
        var session = Session.getDefaultInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(gmailProperties.getUsername(), gmailProperties.getPassword());
            }
        });

        var message = new MimeMessage(session);
        var mimeHelper = new MimeMessageHelper(message);
        mimeHelper.setFrom(gmailProperties.getUsername());
        mimeHelper.setTo(emailRequest.getTo());
        mimeHelper.setSubject(emailRequest.getSubject());
        mimeHelper.setText(emailRequest.getContent(), emailRequest.isHtml());

        Transport.send(message);

        LOG.info("Email [{} -> {}] sent successfully", gmailProperties.getUsername(), emailRequest.getTo());
    }
}
