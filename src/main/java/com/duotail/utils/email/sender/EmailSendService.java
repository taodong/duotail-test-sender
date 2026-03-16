package com.duotail.utils.email.sender;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Properties;


@Slf4j
@Service
public class EmailSendService {

    private final Properties properties = new Properties();

    private final JavaMailSender javaMailSender;

    public EmailSendService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    public void sendEmail(EmailRequest emailRequest) throws MessagingException {
        var message = new MimeMessage(Session.getDefaultInstance(properties, null));
        var mimeHelper = new MimeMessageHelper(message);
        mimeHelper.setFrom(emailRequest.getFrom());
        mimeHelper.setTo(emailRequest.getTo().toArray(new String[0]));
        if (CollectionUtils.isNotEmpty(emailRequest.getCc())) {
            mimeHelper.setCc(emailRequest.getCc().toArray(new String[0]));
        }
        if (CollectionUtils.isNotEmpty(emailRequest.getBcc())) {
            mimeHelper.setBcc(emailRequest.getBcc().toArray(new String[0]));
        }
        if (StringUtils.isNotBlank(emailRequest.getMessageId())) {
            message.setHeader("Message-ID", emailRequest.getMessageId());
        }
        mimeHelper.setSubject(emailRequest.getSubject());
        mimeHelper.setText(emailRequest.getContent(), true);

        for (var entry : emailRequest.getExtraHeaders().entrySet()) {
            message.addHeader(entry.getKey(), entry.getValue());
        }

        javaMailSender.send(message);
    }

    public void sendEmailInFile(InputStream emailFile) throws MessagingException, UnsupportedEncodingException {
        var message = new MimeMessage(Session.getDefaultInstance(properties, null), emailFile);
        var from = new InternetAddress("1_a_test2.user2_56q@1cxym4dev.info", "Test2 User2");
        LOG.info("From address signed: {}", from.getAddress());
        javaMailSender.send(message);
    }

    public void sendEmails(Collection<EmailRequest> emailRequests) {
        emailRequests.forEach(emailRequest -> {
            try {
                sendEmail(emailRequest);
            } catch (MessagingException e) {
                LOG.error("Failed to send email", e);
            }
        });
    }
}
