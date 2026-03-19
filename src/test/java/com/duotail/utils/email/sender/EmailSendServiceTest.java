package com.duotail.utils.email.sender;

import com.duotail.utils.email.sender.permission.ContactPermission;
import com.duotail.utils.email.sender.permission.PermissionException;
import com.duotail.utils.email.sender.permission.SenderPermission;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SuppressWarnings("unused")
@ExtendWith(MockitoExtension.class)
class EmailSendServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    private EmailSendService emailSendService;

    @BeforeEach
    void setUp() {
        emailSendService = new EmailSendService(javaMailSender, allowAllPermission());
    }

    @Test
    void sendEmailAllowsFormattedAddressesWhenPermissionsMatch() throws MessagingException, PermissionException {
        emailSendService = new EmailSendService(
                javaMailSender,
                new SenderPermission(
                        new ContactPermission(false, List.of("whatever.com"), List.of()),
                        new ContactPermission(false, List.of("abc.com", "ppp.con", "fidsf.com", "bfds.com"), List.of()),
                        10
                )
        );

        var emailRequest = emailRequest("fn ln <fn.ln@whatever.com>", Set.of("abc <fsf@abc.com>", "ppp@ppp.con"));
        emailRequest.setCc(Set.of("ffw@fidsf.com"));
        emailRequest.setBcc(Set.of("bcc1@bfds.com"));

        try(MockedConstruction<MimeMessageHelper> mocked = mockConstruction(MimeMessageHelper.class)) {
            emailSendService.sendEmail(emailRequest);
            MimeMessageHelper mockHelper = mocked.constructed().getFirst();
            verify(mockHelper).setFrom("fn ln <fn.ln@whatever.com>");
            verify(mockHelper).setTo(any(String[].class));
            verify(mockHelper).setCc(any(String[].class));
            verify(mockHelper).setBcc(any(String[].class));
            verify(mockHelper).setSubject("subject");
            verify(mockHelper).setText("content", true);
            verify(javaMailSender).send(any(MimeMessage.class));
        }

    }

    @Test
    void sendEmailThrowsWhenSenderIsNotAuthorized() {
        emailSendService = new EmailSendService(
                javaMailSender,
                new SenderPermission(
                        new ContactPermission(false, List.of("allowed.com"), List.of()),
                        new ContactPermission(true, List.of(), List.of()),
                        10
                )
        );

        PermissionException exception = assertThrows(PermissionException.class,
                () -> emailSendService.sendEmail(emailRequest("Sender <denied@example.com>", Set.of("receiver@example.com"))));

        assertEquals("Sender is not authorized: Sender <denied@example.com>", exception.getMessage());
        verifyNoInteractions(javaMailSender);
    }

    @Test
    void sendEmailThrowsWhenRecipientIsNotAuthorized() {
        emailSendService = new EmailSendService(
                javaMailSender,
                new SenderPermission(
                        new ContactPermission(true, List.of(), List.of()),
                        new ContactPermission(false, List.of("allowed.com"), List.of()),
                        10
                )
        );

        var emailRequest = emailRequest("sender@example.com", Set.of("allowed@allowed.com"));
        emailRequest.setCc(Set.of("blocked@blocked.com"));

        PermissionException exception = assertThrows(PermissionException.class,
                () -> emailSendService.sendEmail(emailRequest));

        assertEquals("Recipient is not authorized in cc: blocked@blocked.com", exception.getMessage());
        verifyNoInteractions(javaMailSender);
    }

    @Test
    void sendEmailsThrowsWhenBatchSizeExceedsAllowedLimit() {
        emailSendService = new EmailSendService(javaMailSender, new SenderPermission(
                new ContactPermission(true, List.of(), List.of()),
                new ContactPermission(true, List.of(), List.of()),
                1
        ));

        PermissionException exception = assertThrows(PermissionException.class,
                () -> emailSendService.sendEmails(List.of(
                        emailRequest("sender1@example.com", Set.of("receiver1@example.com")),
                        emailRequest("sender2@example.com", Set.of("receiver2@example.com"))
                )));

        assertEquals("Batch size 2 exceeds allowed limit of 1 emails.", exception.getMessage());
        verifyNoInteractions(javaMailSender);
    }

    @Test
    void sendEmailsValidatesAllPermissionsBeforeSendingBatch() {
        emailSendService = new EmailSendService(
                javaMailSender,
                new SenderPermission(
                        new ContactPermission(true, List.of(), List.of()),
                        new ContactPermission(false, List.of("allowed.com"), List.of()),
                        10
                )
        );

        PermissionException exception = assertThrows(PermissionException.class,
                () -> emailSendService.sendEmails(List.of(
                        emailRequest("sender@example.com", Set.of("allowed@allowed.com")),
                        emailRequest("sender@example.com", Set.of("blocked@blocked.com"))
                )));

        assertEquals("Recipient is not authorized in to: blocked@blocked.com", exception.getMessage());
        verifyNoInteractions(javaMailSender);
    }

    @Test
    void sendEmailInFileAppliesOverridesBeforeSending() throws Exception {
        var eml = "From: original@example.com\r\n"
                + "To: old-to@example.com\r\n"
                + "Cc: old-cc@example.com\r\n"
                + "Subject: Override test\r\n"
                + "\r\n"
                + "Body";

        emailSendService.sendEmailInFile(
                new ByteArrayInputStream(eml.getBytes(StandardCharsets.UTF_8)),
                "New Sender <sender@allowed.com>",
                List.of("to1@allowed.com", "to2@allowed.com"),
                List.of("cc@allowed.com"),
                List.of("bcc@allowed.com")
        );

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());
        MimeMessage sentMessage = messageCaptor.getValue();

        InternetAddress from = (InternetAddress) sentMessage.getFrom()[0];
        assertEquals("sender@allowed.com", from.getAddress());
        assertEquals(2, sentMessage.getRecipients(Message.RecipientType.TO).length);
        assertEquals(1, sentMessage.getRecipients(Message.RecipientType.CC).length);
        assertEquals(1, sentMessage.getRecipients(Message.RecipientType.BCC).length);
    }

    @Test
    void sendEmailInFileThrowsWhenEmlRecipientIsNotAuthorized() {
        emailSendService = new EmailSendService(
                javaMailSender,
                new SenderPermission(
                        new ContactPermission(true, List.of(), List.of()),
                        new ContactPermission(false, List.of("allowed.com"), List.of()),
                        10
                )
        );

        var eml = "From: sender@example.com\r\n"
                + "To: blocked@blocked.com\r\n"
                + "Subject: Permission test\r\n"
                + "\r\n"
                + "Body";

        PermissionException exception = assertThrows(PermissionException.class,
                () -> emailSendService.sendEmailInFile(new ByteArrayInputStream(eml.getBytes(StandardCharsets.UTF_8))));

        assertEquals("Recipient is not authorized in to: blocked@blocked.com", exception.getMessage());
        verifyNoInteractions(javaMailSender);
    }

    @Test
    void sendEmailInFileThrowsWhenOverrideSenderIsNotAuthorized() {
        emailSendService = new EmailSendService(
                javaMailSender,
                new SenderPermission(
                        new ContactPermission(false, List.of("allowed.com"), List.of()),
                        new ContactPermission(true, List.of(), List.of()),
                        10
                )
        );

        var eml = "From: sender@allowed.com\r\n"
                + "To: receiver@example.com\r\n"
                + "Subject: Permission test\r\n"
                + "\r\n"
                + "Body";

        PermissionException exception = assertThrows(PermissionException.class,
                () -> emailSendService.sendEmailInFile(
                        new ByteArrayInputStream(eml.getBytes(StandardCharsets.UTF_8)),
                        "Denied Sender <sender@blocked.com>",
                        null,
                        null,
                        null
                ));

        assertEquals("Sender is not authorized: Denied Sender <sender@blocked.com>", exception.getMessage());
        verifyNoInteractions(javaMailSender);
    }

    private EmailRequest emailRequest(String from, Set<String> to) {
        var emailRequest = new EmailRequest();
        emailRequest.setFrom(from);
        emailRequest.setTo(to);
        emailRequest.setSubject("subject");
        emailRequest.setContent("content");
        return emailRequest;
    }

    private SenderPermission allowAllPermission() {
        return new SenderPermission(
                new ContactPermission(true, List.of(), List.of()),
                new ContactPermission(true, List.of(), List.of()),
                100
        );
    }
}