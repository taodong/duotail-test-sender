package com.duotail.utils.email.sender.mcp;

import com.duotail.utils.email.sender.EmailRequest;
import com.duotail.utils.email.sender.EmailSendService;
import com.duotail.utils.email.sender.permission.PermissionException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class McpToolService {

    private final EmailSendService emailSendService;
    private final ObjectMapper objectMapper;

    public McpToolService(EmailSendService emailSendService, ObjectMapper objectMapper) {
        this.emailSendService = emailSendService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listTools() {
        return List.of(
                Map.of(
                        "name", "send_email",
                        "description", "Send one HTML email using the same contract as POST /api/email.",
                        "inputSchema", singleEmailSchema()
                ),
                Map.of(
                        "name", "send_batch_emails",
                        "description", "Best-effort send for multiple emails, mirroring POST /api/emails.",
                        "inputSchema", batchEmailSchema()
                ),
                Map.of(
                        "name", "send_eml_file_base64",
                        "description", "Send a raw .eml payload represented as base64.",
                        "inputSchema", emlSchema()
                )
        );
    }

    public Map<String, Object> callTool(String name, Map<String, Object> arguments) throws Exception {
        return switch (name) {
            case "send_email" -> sendEmail(arguments);
            case "send_batch_emails" -> sendBatch(arguments);
            case "send_eml_file_base64" -> sendEml(arguments);
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }

    private Map<String, Object> sendEmail(Map<String, Object> arguments) throws Exception {
        var request = objectMapper.convertValue(arguments, EmailRequest.class);
        emailSendService.sendEmail(request);
        return toolTextResult("Email sent.");
    }

    private Map<String, Object> sendBatch(Map<String, Object> arguments) throws PermissionException {
        var emailsRaw = arguments.get("emails");
        if (emailsRaw == null) {
            throw new IllegalArgumentException("Missing required field: emails");
        }

        Collection<EmailRequest> emails = objectMapper.convertValue(emailsRaw, new TypeReference<List<EmailRequest>>() {});
        emailSendService.sendEmails(emails);
        return toolTextResult("Batch send triggered for " + emails.size() + " emails.");
    }

    private Map<String, Object> sendEml(Map<String, Object> arguments) throws Exception {
        var emlBase64 = (String) arguments.get("emlBase64");
        if (StringUtils.isBlank(emlBase64)) {
            throw new IllegalArgumentException("Missing required field: emlBase64");
        }

        String from = objectMapper.convertValue(arguments.get("from"), String.class);
        List<String> to = toStringList(arguments.get("to"));
        List<String> cc = toStringList(arguments.get("cc"));
        List<String> bcc = toStringList(arguments.get("bcc"));

        byte[] emlBytes;
        try {
            emlBytes = Base64.getDecoder().decode(emlBase64.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid base64 value for emlBase64", ex);
        }

        try (var stream = new ByteArrayInputStream(emlBytes)) {
            emailSendService.sendEmailInFile(stream, from, to, cc, bcc);
        }
        return toolTextResult("EML email sent.");
    }

    private Map<String, Object> toolTextResult(String message) {
        return Map.of(
                "content", List.of(Map.of("type", "text", "text", message)),
                "isError", false
        );
    }

    private Map<String, Object> singleEmailSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("from", "to", "subject", "content"),
                "properties", Map.of(
                        "from", Map.of("type", "string"),
                        "to", Map.of("type", "array", "items", Map.of("type", "string")),
                        "cc", Map.of("type", "array", "items", Map.of("type", "string")),
                        "bcc", Map.of("type", "array", "items", Map.of("type", "string")),
                        "subject", Map.of("type", "string"),
                        "content", Map.of("type", "string"),
                        "messageId", Map.of("type", "string"),
                        "extraHeaders", Map.of("type", "object", "additionalProperties", Map.of("type", "string"))
                )
        );
    }

    private Map<String, Object> batchEmailSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("emails"),
                "properties", Map.of(
                        "emails", Map.of("type", "array", "items", singleEmailSchema())
                )
        );
    }

    private Map<String, Object> emlSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("emlBase64"),
                "properties", Map.of(
                        "emlBase64", Map.of("type", "string", "description", "Raw .eml content encoded in base64"),
                        "from", Map.of("type", "string"),
                        "to", Map.of("type", "array", "items", Map.of("type", "string")),
                        "cc", Map.of("type", "array", "items", Map.of("type", "string")),
                        "bcc", Map.of("type", "array", "items", Map.of("type", "string"))
                )
        );
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, new TypeReference<List<String>>() {});
    }
}


