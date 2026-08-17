package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.EmailAttachment;
import com.duotail.utils.email.mailhog.dto.EmailContent;
import com.duotail.utils.email.mailhog.dto.EmailHeader;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Parses a raw RFC-822 message into headers and transfer-decoded body parts.
 * <p>
 * Decoding is the point of this class: MailHog serves the message verbatim, so a
 * quoted-printable body arrives with {@code =3D} escapes and {@code =\r\n} soft line breaks
 * that split URLs mid-token. {@link Part#getContent()} undoes both, which is what keeps a
 * confirmation link usable by the caller.
 */
@Slf4j
@Component
public class EmlContentExtractor {

    private static final String TRUNCATION_MARKER = "\n...[truncated]";

    private final int maxBodyChars;

    public EmlContentExtractor(@Value("${app.mailhog.max-body-chars:100000}") int maxBodyChars) {
        this.maxBodyChars = maxBodyChars;
    }

    public EmailContent extract(String id, byte[] eml) {
        var accumulator = new Accumulator();
        try (var stream = new ByteArrayInputStream(eml)) {
            var message = new MimeMessage(Session.getDefaultInstance(new Properties(), null), stream);
            collectHeaders(message, accumulator);
            collectParts(message, accumulator);
        } catch (MessagingException | IOException e) {
            LOG.warn("Failed to parse eml for MailHog message id={}", id, e);
            throw new MailhogMessageParseException(id, e);
        }

        var textBody = truncate(accumulator.textBody, accumulator);
        var htmlBody = truncate(accumulator.htmlBody, accumulator);
        return new EmailContent(
                id,
                accumulator.headers,
                textBody,
                htmlBody,
                accumulator.attachments,
                accumulator.truncated);
    }

    private void collectHeaders(MimeMessage message, Accumulator accumulator) throws MessagingException {
        var headers = message.getAllHeaders();
        while (headers.hasMoreElements()) {
            var header = headers.nextElement();
            accumulator.headers.add(new EmailHeader(header.getName(), header.getValue()));
        }
    }

    /**
     * Walks the MIME tree depth-first, keeping the first inline text/plain and text/html parts
     * found and recording every attachment as metadata only.
     */
    private void collectParts(Part part, Accumulator accumulator) throws MessagingException, IOException {
        if (isAttachment(part)) {
            accumulator.attachments.add(new EmailAttachment(
                    part.getFileName(), baseContentType(part), part.getSize()));
            return;
        }

        if (part.isMimeType("multipart/*")) {
            if (part.getContent() instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    collectParts(multipart.getBodyPart(i), accumulator);
                }
            }
            return;
        }

        if (part.isMimeType("text/plain") && accumulator.textBody == null) {
            accumulator.textBody = asString(part);
        } else if (part.isMimeType("text/html") && accumulator.htmlBody == null) {
            accumulator.htmlBody = asString(part);
        }
    }

    private boolean isAttachment(Part part) throws MessagingException {
        return Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null;
    }

    private String baseContentType(Part part) throws MessagingException {
        var contentType = part.getContentType();
        if (contentType == null) {
            return null;
        }
        // Strip parameters (charset, name, ...) so the reported type stays readable
        var separator = contentType.indexOf(';');
        return (separator < 0 ? contentType : contentType.substring(0, separator)).trim();
    }

    private String asString(Part part) throws MessagingException, IOException {
        var content = part.getContent();
        return content instanceof String text ? text : String.valueOf(content);
    }

    private String truncate(String body, Accumulator accumulator) {
        if (body == null || body.length() <= maxBodyChars) {
            return body;
        }
        accumulator.truncated = true;
        return body.substring(0, maxBodyChars) + TRUNCATION_MARKER;
    }

    /**
     * Mutable state for a single extraction. Held locally so the component stays stateless.
     */
    private static final class Accumulator {
        private final List<EmailHeader> headers = new ArrayList<>();
        private final List<EmailAttachment> attachments = new ArrayList<>();
        private String textBody;
        private String htmlBody;
        private boolean truncated;
    }
}
