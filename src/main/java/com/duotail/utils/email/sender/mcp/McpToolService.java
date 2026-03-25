package com.duotail.utils.email.sender.mcp;

import com.duotail.utils.email.sender.EmailRequest;
import com.duotail.utils.email.sender.EmailSendService;
import com.duotail.utils.email.sender.permission.PermissionException;
import jakarta.mail.MessagingException;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
public class McpToolService {

    private final EmailSendService emailSendService;

    public McpToolService(EmailSendService emailSendService) {
        this.emailSendService = emailSendService;
    }

    @McpTool(description = "Send one HTML email using a structured request object")
    public String sendEmail(EmailRequest request) throws PermissionException, MessagingException {
        emailSendService.sendEmail(request);
        return "Email sent successfully.";
    }

    @McpTool(description = "Best-effort send for multiple emails in a single batch")
    public String sendBatchEmails(
            @McpToolParam(description = "List of email request objects") List<EmailRequest> emails
    ) throws PermissionException {
        emailSendService.sendEmails(emails);
        return "Batch send triggered for " + emails.size() + " emails.";
    }

    @McpTool(description = "Send a raw .eml payload represented as base64")
    public String sendEmlFileBase64(
            @McpToolParam(description = "Raw .eml content encoded in base64") String emlBase64,
            @McpToolParam(description = "The optional `from` address to be replaced in the email header") String from,
            @McpToolParam(description = "a list of optional `to` address to be replaced in the email header") List<String> to,
            @McpToolParam(description = "a list of optional `cc` address to be replaced in the email header") List<String> cc,
            @McpToolParam(description = "a list of optional `bcc` address to be replaced in the email header") List<String> bcc
    ) throws Exception {
        byte[] emlBytes = Base64.getDecoder().decode(emlBase64.getBytes(StandardCharsets.UTF_8));

        try (var stream = new ByteArrayInputStream(emlBytes)) {
            emailSendService.sendEmailInFile(stream, from, to, cc, bcc);
        }
        return "EML email sent successfully.";
    }
}