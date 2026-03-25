package com.duotail.utils.email.sender.mcp;

import com.duotail.utils.email.sender.EmailRequest;
import com.duotail.utils.email.sender.EmailSendService;
import com.duotail.utils.email.sender.permission.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class McpToolServiceTest {

    private static final String RAW_EML = "From: sender@example.com\r\nTo: receiver@example.com\r\nSubject: Test\r\n\r\nBody";

    @Mock
    private EmailSendService emailSendService;

    private McpToolService mcpToolService;

    @BeforeEach
    void setUp() {
        mcpToolService = new McpToolService(emailSendService);
    }

    @Test
    void sendEmailDelegatesToEmailSendService() throws Exception {
        var request = emailRequest();

        var result = mcpToolService.sendEmail(request);

        verify(emailSendService).sendEmail(request);
        assertEquals("Email sent successfully.", result);
    }

    @Test
    void sendEmailPropagatesPermissionViolation() throws Exception {
        var request = emailRequest();

        doThrow(new PermissionException("Sender is not authorized: denied@example.com"))
                .when(emailSendService)
                .sendEmail(request);

        PermissionException exception = assertThrows(PermissionException.class,
                () -> mcpToolService.sendEmail(request));

        assertEquals("Sender is not authorized: denied@example.com", exception.getMessage());
    }

    @Test
    void sendBatchEmailsDelegatesToEmailSendService() throws Exception {
        var emails = List.of(emailRequest(), emailRequest("batch-sender@example.com", Set.of("batch-recipient@example.com")));

        var result = mcpToolService.sendBatchEmails(emails);

        verify(emailSendService).sendEmails(emails);
        assertEquals("Batch send triggered for 2 emails.", result);
    }

    @Test
    void sendBatchEmailsPropagatesPermissionViolation() throws Exception {
        var emails = List.of(
                emailRequest(),
                emailRequest("batch-sender@example.com", Set.of("batch-recipient@example.com"))
        );

        doThrow(new PermissionException("Batch size 2 exceeds allowed limit of 1 emails."))
                .when(emailSendService)
                .sendEmails(emails);

        PermissionException exception = assertThrows(PermissionException.class,
                () -> mcpToolService.sendBatchEmails(emails));

        assertEquals("Batch size 2 exceeds allowed limit of 1 emails.", exception.getMessage());
    }

    @Test
    void sendEmlFileBase64DelegatesDecodedPayloadToEmailSendService() throws Exception {
        var base64 = base64Eml(RAW_EML);

        var result = mcpToolService.sendEmlFileBase64(base64, null, null, null, null);

        ArgumentCaptor<InputStream> inputStreamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(emailSendService).sendEmailInFile(inputStreamCaptor.capture(), isNull(), isNull(), isNull(), isNull());
        assertEquals(RAW_EML, new String(inputStreamCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8));
        assertEquals("EML email sent successfully.", result);
    }

    @Test
    void sendEmlFileBase64PassesAddressOverridesToEmailSendService() throws Exception {
        var base64 = base64Eml(RAW_EML);

        var result = mcpToolService.sendEmlFileBase64(
                base64,
                "Sender <sender@example.com>",
                List.of("to@example.com"),
                List.of("cc@example.com"),
                List.of("bcc@example.com")
        );

        verify(emailSendService).sendEmailInFile(
                any(InputStream.class),
                eq("Sender <sender@example.com>"),
                eq(List.of("to@example.com")),
                eq(List.of("cc@example.com")),
                eq(List.of("bcc@example.com"))
        );
        assertEquals("EML email sent successfully.", result);
    }

    @Test
    void sendEmlFileBase64PropagatesPermissionViolation() throws Exception {
        var base64 = base64Eml(RAW_EML);

        doThrow(new PermissionException("Sender is not authorized: denied@example.com"))
                .when(emailSendService)
                .sendEmailInFile(any(InputStream.class), any(), any(), any(), any());

        PermissionException exception = assertThrows(PermissionException.class,
                () -> mcpToolService.sendEmlFileBase64(base64, null, null, null, null));

        assertEquals("Sender is not authorized: denied@example.com", exception.getMessage());
    }

    @Test
    void sendEmlFileBase64RejectsInvalidBase64WithoutCallingEmailSendService() {
        assertThrows(IllegalArgumentException.class,
                () -> mcpToolService.sendEmlFileBase64("not-base64", null, null, null, null));

        verifyNoInteractions(emailSendService);
    }

    private EmailRequest emailRequest() {
        return emailRequest("sender@example.com", Set.of("receiver@example.com"));
    }

    private EmailRequest emailRequest(String from, Set<String> to) {
        var request = new EmailRequest();
        request.setFrom(from);
        request.setTo(to);
        request.setSubject("Test");
        request.setContent("<p>Hello</p>");
        return request;
    }

    @SuppressWarnings("SameParameterValue")
    private String base64Eml(String rawEml) {
        return Base64.getEncoder().encodeToString(rawEml.getBytes(StandardCharsets.UTF_8));
    }
}
