package com.duotail.utils.email.sender;

import jakarta.mail.MessagingException;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Address;
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
import java.util.Collection;
import java.util.List;
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

    public void sendEmailInFile(InputStream emailFile) throws MessagingException, PermissionException {
        sendEmailInFile(emailFile, null, null, null, null);
    }

    public void sendEmailInFile(InputStream emailFile,
                                String from,
                                Collection<String> to,
                                Collection<String> cc,
                                Collection<String> bcc) throws MessagingException, PermissionException {
        var message = new MimeMessage(Session.getDefaultInstance(properties, null), emailFile);
        applyAddressOverrides(message, from, to, cc, bcc);
        validateMimeMessagePermissions(message);
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

    private void applyAddressOverrides(MimeMessage message,
                                       String from,
                                       Collection<String> to,
                                       Collection<String> cc,
                                       Collection<String> bcc) throws MessagingException {
        if (from != null) {
            message.setFrom(parseSingleAddress(from));
        }
        if (to != null) {
            message.setRecipients(Message.RecipientType.TO, parseAddresses(to));
        }
        if (cc != null) {
            message.setRecipients(Message.RecipientType.CC, parseAddresses(cc));
        }
        if (bcc != null) {
            message.setRecipients(Message.RecipientType.BCC, parseAddresses(bcc));
        }
    }

    private void validateMimeMessagePermissions(MimeMessage message) throws MessagingException, PermissionException {
        validateAddresses(message.getFrom(), senderPermission.from(), "Sender is not authorized: ");
        validateAddresses(message.getRecipients(Message.RecipientType.TO), senderPermission.to(), "Recipient is not authorized in to: ");
        validateAddresses(message.getRecipients(Message.RecipientType.CC), senderPermission.to(), "Recipient is not authorized in cc: ");
        validateAddresses(message.getRecipients(Message.RecipientType.BCC), senderPermission.to(), "Recipient is not authorized in bcc: ");
    }

    private void validateAddresses(Address[] addresses,
                                   ContactPermission permission,
                                   String messagePrefix) throws PermissionException {
        if (addresses == null) {
            return;
        }

        for (Address address : addresses) {
            validateContact(address.toString(), permission, messagePrefix);
        }
    }

    private InternetAddress parseSingleAddress(String value) throws AddressException {
        InternetAddress[] parsed = InternetAddress.parse(value, true);
        if (parsed.length == 0) {
            throw new AddressException("Address value must not be empty", value);
        }
        return parsed[0];
    }

    private Address[] parseAddresses(Collection<String> rawAddresses) throws AddressException {
        if (CollectionUtils.isEmpty(rawAddresses)) {
            return null;
        }

        List<Address> parsedAddresses = new java.util.ArrayList<>();
        for (String rawAddress : rawAddresses) {
            if (StringUtils.isBlank(rawAddress)) {
                continue;
            }
            parsedAddresses.addAll(List.of(InternetAddress.parse(rawAddress, true)));
        }

        if (parsedAddresses.isEmpty()) {
            return null;
        }

        return parsedAddresses.toArray(new Address[0]);
    }
}
