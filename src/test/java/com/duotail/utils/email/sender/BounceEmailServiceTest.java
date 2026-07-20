package com.duotail.utils.email.sender;

import com.duotail.utils.email.sender.permission.PermissionException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BounceEmailServiceTest {

    private static final String MAILER_DAEMON = "MAILER-DAEMON@mail.duotail.test";
    private static final String REPORTING_MTA = "dns; mail.duotail.test";

    @Mock
    private EmailSendService emailSendService;

    private BounceEmailService bounceEmailService;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        bounceEmailService = new BounceEmailService(emailSendService, validator, MAILER_DAEMON, REPORTING_MTA);
    }

    @Test
    void hardBounceBuildsRfc3464DsnAddressedToOriginalSender() throws Exception {
        var request = request(BounceType.HARD);

        bounceEmailService.sendBounce(request);

        var message = capturedMessage();
        assertEquals(MAILER_DAEMON, message.getFrom()[0].toString());
        assertEquals("sender@example.com", message.getRecipients(Message.RecipientType.TO)[0].toString());
        assertTrue(message.getSubject().contains("Failure"), "subject should mark a failure");

        var contentType = message.getContentType();
        assertTrue(contentType.startsWith("multipart/report"), "expected multipart/report but was " + contentType);
        assertTrue(contentType.contains("report-type=delivery-status"), "missing report-type parameter: " + contentType);

        var multipart = (MimeMultipart) message.getContent();
        assertEquals(3, multipart.getCount());
        assertTrue(multipart.getBodyPart(0).getContentType().startsWith("text/plain"));
        assertTrue(multipart.getBodyPart(1).getContentType().startsWith("message/delivery-status"));
        assertTrue(multipart.getBodyPart(2).getContentType().startsWith("message/rfc822-headers"));

        var raw = asString(message);
        assertTrue(raw.contains("Final-Recipient: rfc822; failed@example.com"));
        assertTrue(raw.contains("Action: failed"));
        assertTrue(raw.contains("Status: 5.1.1"));
        assertTrue(raw.contains("Reporting-MTA: dns; mail.duotail.test"));
        assertTrue(raw.contains("Diagnostic-Code: smtp; 550 5.1.1 User unknown"));
        // original headers echoed in the message/rfc822-headers part
        assertTrue(raw.contains("Subject: Your order confirmation"));
    }

    @Test
    void softBounceUsesTransientActionAndDefaultStatus() throws Exception {
        var request = request(BounceType.SOFT);

        bounceEmailService.sendBounce(request);

        var raw = asString(capturedMessage());
        assertTrue(raw.contains("Action: delayed"));
        assertTrue(raw.contains("Status: 4.2.2"));
        assertTrue(raw.contains("Diagnostic-Code: smtp; 452 4.2.2 Mailbox over quota"));
    }

    @Test
    void explicitStatusCodeAndDiagnosticOverrideDefaults() throws Exception {
        var request = request(BounceType.HARD);
        request.setStatusCode("5.2.2");
        request.setDiagnosticText("smtp; 552 mailbox is completely full");

        bounceEmailService.sendBounce(request);

        var raw = asString(capturedMessage());
        assertTrue(raw.contains("Status: 5.2.2"));
        assertTrue(raw.contains("Diagnostic-Code: smtp; 552 mailbox is completely full"));
    }

    @Test
    void suppliedMessageIdIsReferenced() throws Exception {
        var request = request(BounceType.HARD);
        request.setOriginalMessageId("<orig-123@example.com>");

        bounceEmailService.sendBounce(request);

        var message = capturedMessage();
        assertEquals("<orig-123@example.com>", message.getHeader("In-Reply-To")[0]);
        assertTrue(asString(message).contains("Message-ID: <orig-123@example.com>"));
    }

    @Test
    void unmappedStatusCodeFallsBackToGenericDiagnostic() throws Exception {
        var hard = request(BounceType.HARD);
        hard.setStatusCode("5.9.9"); // valid HARD class, absent from the built-in map

        bounceEmailService.sendBounce(hard);

        var raw = asString(capturedMessage());
        assertTrue(raw.contains("Status: 5.9.9"));
        assertTrue(raw.contains("Diagnostic-Code: smtp; 550 5.0.0 Permanent failure"));
    }

    @Test
    void unmappedSoftStatusCodeFallsBackToTransientDiagnostic() throws Exception {
        var soft = request(BounceType.SOFT);
        soft.setStatusCode("4.9.9"); // valid SOFT class, absent from the built-in map

        bounceEmailService.sendBounce(soft);

        var raw = asString(capturedMessage());
        assertTrue(raw.contains("Diagnostic-Code: smtp; 451 4.0.0 Temporary failure"));
    }

    @Test
    void rejectsInvalidRequestThatSkippedBeanValidation() {
        var invalid = request(BounceType.HARD);
        invalid.setBounceType(null); // an MCP caller can omit this since @Valid does not run there

        assertThrows(ConstraintViolationException.class, () -> bounceEmailService.sendBounce(invalid));
        verifyNoInteractions(emailSendService);
    }

    @Test
    void propagatesPermissionExceptionFromSend() throws Exception {
        doThrow(new PermissionException("Sender is not authorized: " + MAILER_DAEMON))
                .when(emailSendService).sendMimeMessage(org.mockito.ArgumentMatchers.any());

        assertThrows(PermissionException.class, () -> bounceEmailService.sendBounce(request(BounceType.HARD)));
    }

    private MimeMessage capturedMessage() throws MessagingException, PermissionException {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(emailSendService).sendMimeMessage(captor.capture());
        return captor.getValue();
    }

    private String asString(MimeMessage message) throws Exception {
        var out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private BounceRequest request(BounceType type) {
        var request = new BounceRequest();
        request.setOriginalFrom("sender@example.com");
        request.setOriginalTo("failed@example.com");
        request.setOriginalSubject("Your order confirmation");
        request.setBounceType(type);
        return request;
    }
}
