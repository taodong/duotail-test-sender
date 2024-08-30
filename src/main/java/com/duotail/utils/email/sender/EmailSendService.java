package com.duotail.utils.email.sender;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

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
        mimeHelper.setSubject(emailRequest.getSubject());
        mimeHelper.setText(emailRequest.getContent());

        javaMailSender.send(message);
    }
}
