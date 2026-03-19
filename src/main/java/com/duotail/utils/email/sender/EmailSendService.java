package com.duotail.utils.email.sender;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import com.duotail.utils.email.sender.permission.ContactPermission;
import com.duotail.utils.email.sender.permission.PermissionException;
import com.duotail.utils.email.sender.permission.SenderPermission;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;


@Slf4j
@Service
public class EmailSendService {

    private final Properties properties = new Properties();

    private final JavaMailSender javaMailSender;
    private final SenderPermission senderPermission;

    public EmailSendService(JavaMailSender javaMailSender, SenderPermission senderPermission) {
        this.javaMailSender = javaMailSender;
        this.senderPermission = senderPermission;
    }

    public void sendEmail(EmailRequest emailRequest) throws MessagingException, PermissionException {
        validateEmailPermissions(emailRequest);
        sendValidatedEmail(emailRequest);
    }

    private void sendValidatedEmail(EmailRequest emailRequest) throws MessagingException {
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

        for (var entry : extraHeaders(emailRequest).entrySet()) {
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

    public void sendEmails(Collection<EmailRequest> emailRequests) throws PermissionException {
        validateBatchPermissions(emailRequests);
        emailRequests.forEach(emailRequest -> {
            try {
                sendValidatedEmail(emailRequest);
            } catch (MessagingException e) {
                LOG.error("Failed to send email", e);
            }
        });
    }

    private void validateBatchPermissions(Collection<EmailRequest> emailRequests) throws PermissionException {
        if (emailRequests.size() > senderPermission.batchSize()) {
            throw new PermissionException("Batch size " + emailRequests.size()
                    + " exceeds allowed limit of " + senderPermission.batchSize() + " emails.");
        }

        for (EmailRequest emailRequest : emailRequests) {
            validateEmailPermissions(emailRequest);
        }
    }

    private void validateEmailPermissions(EmailRequest emailRequest) throws PermissionException {
        validateContact(emailRequest.getFrom(), senderPermission.from(), "Sender is not authorized: ");
        validateContacts(emailRequest.getTo(), senderPermission.to(), "Recipient is not authorized in to: ");
        validateContacts(emailRequest.getCc(), senderPermission.to(), "Recipient is not authorized in cc: ");
        validateContacts(emailRequest.getBcc(), senderPermission.to(), "Recipient is not authorized in bcc: ");
    }

    private void validateContacts(Set<String> contacts,
                                  ContactPermission permission,
                                  String messagePrefix) throws PermissionException {
        if (CollectionUtils.isEmpty(contacts)) {
            return;
        }

        for (String contact : contacts) {
            validateContact(contact, permission, messagePrefix);
        }
    }

    private void validateContact(String contact,
                                 ContactPermission permission,
                                 String messagePrefix) throws PermissionException {
        String address = extractEmailAddress(contact);
        if (address == null) {
            throw new PermissionException(messagePrefix + contact);
        }

        if (!permission.hasPermission(address)) {
            throw new PermissionException(messagePrefix + contact);
        }
    }

    private String extractEmailAddress(String contact) {
        if (StringUtils.isBlank(contact)) {
            return null;
        }

        try {
            InternetAddress[] addresses = InternetAddress.parse(contact, true);
            if (addresses.length == 0 || StringUtils.isBlank(addresses[0].getAddress())) {
                return null;
            }
            return addresses[0].getAddress();
        } catch (AddressException ex) {
            return null;
        }
    }

    private Map<String, String> extraHeaders(EmailRequest emailRequest) {
        return emailRequest.getExtraHeaders() == null ? Map.of() : emailRequest.getExtraHeaders();
    }
}
