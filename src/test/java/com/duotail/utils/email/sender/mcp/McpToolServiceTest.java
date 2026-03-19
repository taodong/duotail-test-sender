package com.duotail.utils.email.sender.mcp;

import com.duotail.utils.email.sender.EmailSendService;
import com.duotail.utils.email.sender.permission.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class McpToolServiceTest {

    @Mock
    private EmailSendService emailSendService;

    private McpToolService mcpToolService;

    @BeforeEach
    void setUp() {
        mcpToolService = new McpToolService(emailSendService, new ObjectMapper());
    }

    @Test
    void sendEmailToolDispatchesToEmailSendService() throws Exception {
        var arguments = Map.<String, Object>of(
                "from", "sender@example.com",
                "to", List.of("receiver@example.com"),
                "subject", "Test",
                "content", "<p>Hello</p>"
        );

        var result = mcpToolService.callTool("send_email", arguments);

        verify(emailSendService).sendEmail(any());
        assertFalse((Boolean) result.get("isError"));
    }

    @Test
    void sendBatchToolDispatchesToEmailSendService() throws Exception {
        var arguments = Map.<String, Object>of(
                "emails", List.of(Map.of(
                        "from", "sender@example.com",
                        "to", List.of("receiver@example.com"),
                        "subject", "Batch",
                        "content", "<p>Hello</p>"
                ))
        );

        var result = mcpToolService.callTool("send_batch_emails", arguments);

        verify(emailSendService).sendEmails(any());
        assertFalse((Boolean) result.get("isError"));
    }

    @Test
    void sendBatchToolPropagatesPermissionViolation() throws Exception {
        doThrow(new PermissionException("Batch size 2 exceeds allowed limit of 1 emails."))
                .when(emailSendService)
                .sendEmails(any());

        var arguments = Map.<String, Object>of(
                "emails", List.of(Map.of(
                        "from", "sender@example.com",
                        "to", List.of("receiver@example.com"),
                        "subject", "Batch",
                        "content", "<p>Hello</p>"
                ))
        );

        PermissionException exception = assertThrows(PermissionException.class,
                () -> mcpToolService.callTool("send_batch_emails", arguments));

        assertEquals("Batch size 2 exceeds allowed limit of 1 emails.", exception.getMessage());
    }

    @Test
    void sendEmlToolDispatchesToEmailSendService() throws Exception {
        var base64 = Base64.getEncoder().encodeToString("raw-eml".getBytes(StandardCharsets.UTF_8));

        var result = mcpToolService.callTool("send_eml_file_base64", Map.of("emlBase64", base64));

        verify(emailSendService).sendEmailInFile(any(InputStream.class));
        assertFalse((Boolean) result.get("isError"));
    }

    @Test
    void unknownToolThrowsValidationError() {
        assertThrows(IllegalArgumentException.class, () -> mcpToolService.callTool("unknown", Map.of()));
    }
}


