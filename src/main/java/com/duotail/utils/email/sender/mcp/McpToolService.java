package com.duotail.utils.email.sender.mcp;

import com.duotail.utils.email.mailhog.EmlContentExtractor;
import com.duotail.utils.email.mailhog.MailhogService;
import com.duotail.utils.email.mailhog.dto.EmailContent;
import com.duotail.utils.email.mailhog.dto.MailhogPageResponse;
import com.duotail.utils.email.mailhog.dto.MailhogPath;
import com.duotail.utils.email.sender.BounceEmailService;
import com.duotail.utils.email.sender.BounceRequest;
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
import java.util.stream.Collectors;

@Service
public class McpToolService {

    private final EmailSendService emailSendService;
    private final BounceEmailService bounceEmailService;
    private final MailhogService mailhogService;
    private final EmlContentExtractor emlContentExtractor;

    public McpToolService(EmailSendService emailSendService,
                          BounceEmailService bounceEmailService,
                          MailhogService mailhogService,
                          EmlContentExtractor emlContentExtractor) {
        this.emailSendService = emailSendService;
        this.bounceEmailService = bounceEmailService;
        this.mailhogService = mailhogService;
        this.emlContentExtractor = emlContentExtractor;
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

    @McpTool(description = "Send a mocked bounce (RFC 3464 DSN) back to an original sender for testing bounce handling")
    public String sendBounce(BounceRequest request) throws PermissionException, MessagingException {
        bounceEmailService.sendBounce(request);
        return "Bounce sent successfully.";
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

    @McpTool(description = "List emails captured by MailHog. Returns a plain-text summary of each message.")
    public String listMailhogMessages(
            @McpToolParam(description = "Zero-based start offset") int start,
            @McpToolParam(description = "Max number of messages to return (max 50)") int limit
    ) {
        return formatResponse(mailhogService.getMessages(start, limit));
    }

    @McpTool(description = "Search emails in MailHog. Kind must be 'from', 'to', or 'containing'.")
    public String searchMailhogMessages(
            @McpToolParam(description = "Search kind: 'from', 'to', or 'containing'") String kind,
            @McpToolParam(description = "Search term") String query,
            @McpToolParam(description = "Zero-based start offset") int start,
            @McpToolParam(description = "Max number of results (max 50)") int limit
    ) {
        return formatResponse(mailhogService.search(kind, query, start, limit));
    }

    @McpTool(description = "Get a specific email captured by MailHog by its message ID.")
    public String getMailhogMessage(
            @McpToolParam(description = "The MailHog message ID") String id
    ) {
        var msg = mailhogService.getMessage(id);
        var subject = extractSubject(msg.content() != null ? msg.content().headers() : null);
        var from = msg.from() != null ? msg.from().address() : "(unknown)";
        var to = msg.to() != null
                ? msg.to().stream().map(MailhogPath::address).collect(Collectors.joining(", "))
                : "(unknown)";
        return "Message ID: " + msg.id() + "\n" +
                "Subject: " + subject + "\n" +
                "From: " + from + "\n" +
                "To: " + to + "\n" +
                "Created: " + msg.created();
    }

    @McpTool(description = "Get the full headers and decoded body of an email captured by MailHog by its message ID. "
            + "Use this to read email content such as confirmation or activation links.")
    public String getMailhogMessageContent(
            @McpToolParam(description = "The MailHog message ID") String id
    ) {
        return formatContent(emlContentExtractor.extract(id, mailhogService.getMessageEml(id)));
    }

    @McpTool(description = "Delete a specific email captured by MailHog by its message ID.")
    public String deleteMailhogMessage(
            @McpToolParam(description = "The MailHog message ID") String id
    ) {
        mailhogService.deleteMessage(id);
        return "Message " + id + " deleted successfully.";
    }

    private String formatResponse(MailhogPageResponse response) {
        var sb = new StringBuilder();
        sb.append("Found ").append(response.total()).append(" message(s). Showing ")
                .append(response.count()).append(" from offset ").append(response.start()).append(".\n");

        var items = response.items();
        if (items == null || items.isEmpty()) {
            return sb.append("\nNo messages.").toString();
        }

        sb.append("\n");
        for (int i = 0; i < items.size(); i++) {
            var msg = items.get(i);
            var subject = extractSubject(msg.content() != null ? msg.content().headers() : null);
            var from = msg.from() != null ? msg.from().address() : "(unknown)";
            var to = msg.to() != null
                    ? msg.to().stream().map(MailhogPath::address).collect(Collectors.joining(", "))
                    : "(unknown)";
            sb.append(i + 1).append(". Subject: ").append(subject)
                    .append(" | From: ").append(from)
                    .append(" | To: ").append(to)
                    .append(" | Created: ").append(msg.created())
                    .append(" | ID: ").append(msg.id()).append("\n");
        }
        return sb.toString();
    }

    private String formatContent(EmailContent content) {
        var sb = new StringBuilder("Message ID: ").append(content.id()).append("\n");

        sb.append("\n--- Headers ---\n");
        for (var header : content.headers()) {
            sb.append(header.name()).append(": ").append(header.value()).append("\n");
        }

        if (content.textBody() != null) {
            sb.append("\n--- Body (text/plain) ---\n").append(content.textBody()).append("\n");
        }
        if (content.htmlBody() != null) {
            sb.append("\n--- Body (text/html) ---\n").append(content.htmlBody()).append("\n");
        }
        if (content.textBody() == null && content.htmlBody() == null) {
            sb.append("\n--- Body ---\n(no text or html body)\n");
        }

        var otherParts = content.otherParts();
        if (otherParts != null) {
            for (var part : otherParts) {
                sb.append("\n--- Part (").append(part.contentType()).append(") ---\n")
                        .append(part.content()).append("\n");
            }
        }

        var attachments = content.attachments();
        if (attachments != null && !attachments.isEmpty()) {
            sb.append("\n--- Attachments ---\n");
            for (var attachment : attachments) {
                sb.append(attachment.filename())
                        .append(" (").append(attachment.contentType())
                        .append(", ").append(attachment.size()).append(" bytes)\n");
            }
        }
        return sb.toString();
    }

    private String extractSubject(java.util.Map<String, List<String>> headers) {
        if (headers == null) return "(no subject)";
        var subjects = headers.get("Subject");
        if (subjects == null || subjects.isEmpty()) return "(no subject)";
        return subjects.getFirst();
    }
}
