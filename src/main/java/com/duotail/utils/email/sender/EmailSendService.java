package com.duotail.utils.email.sender;

import io.github.taodong.mail.dkim.Canonicalization;
import io.github.taodong.mail.dkim.DkimMimeMessageHelper;
import io.github.taodong.mail.dkim.DkimSignature;
import io.github.taodong.mail.dkim.DkimSigningService;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Properties;


@Slf4j
@Service
public class EmailSendService {

    private final Properties properties = new Properties();

    private final JavaMailSender javaMailSender;
    private final DkimSignerProperties dkimSignerProperties;
    private final DkimSigningService dkimSigningService;
    private final DkimMimeMessageHelper dkimMimeMessageHelper;

    public EmailSendService(JavaMailSender javaMailSender, DkimSignerProperties dkimSignerProperties, DkimSigningService dkimSigningService, DkimMimeMessageHelper dkimMimeMessageHelper) {
        this.javaMailSender = javaMailSender;
        this.dkimSignerProperties = dkimSignerProperties;
        this.dkimSigningService = dkimSigningService;
        this.dkimMimeMessageHelper = dkimMimeMessageHelper;
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

        signDkim(message, emailRequest.getFrom());
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

    private void signDkim(MimeMessage message, String from) {

        if (dkimSignerProperties.isEnabled() && StringUtils.isNoneBlank(dkimSignerProperties.getPrivateKeyPath(), dkimSignerProperties.getSelector(), dkimSignerProperties.getDomain())) {
            Resource dkimPrivateKey = new FileSystemResource(dkimSignerProperties.getPrivateKeyPath());
            try (var keyInputStream = dkimPrivateKey.getInputStream()) {
                var fromAddress = (new InternetAddress(from)).getAddress();
                var dkimSignature = dkimSigningService.sign(message,
                        dkimMimeMessageHelper.getKPCS8KeyFromInputStream(keyInputStream),
                        dkimSignerProperties.getSelector(),
                        dkimSignerProperties.getDomain(),
                        fromAddress,
                        dkimMimeMessageHelper.getDkimSignHeaders(null),
                        Canonicalization.fromType(dkimSignerProperties.getHeaderCanonicalization()),
                        Canonicalization.fromType(dkimSignerProperties.getBodyCanonicalization()));
                message.setHeader(DkimSignature.DKIM_SIGNATURE_HEADER, dkimSignature);
            } catch (Exception e) {
                LOG.error("Failed to sign DKIM", e);
            }
        }
    }
}
