package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.EmailAttachment;
import com.duotail.utils.email.mailhog.dto.EmailContent;
import com.duotail.utils.email.mailhog.dto.EmailHeader;
import com.duotail.utils.email.mailhog.dto.EmailPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
 * <p>
 * Extraction is deliberately forgiving. A single part that cannot be decoded — an unknown
 * charset such as {@code unknown-8bit} is routine in real bounce traffic — must not cost the
 * caller the headers and the parts that did decode, so failures are contained per part.
 */
@Slf4j
@Component
public class EmlContentExtractor {

    private static final String TRUNCATION_MARKER = "\n...[truncated]";

    private final int maxContentChars;

    public EmlContentExtractor(@Value("${app.mailhog.max-content-chars:100000}") int maxContentChars) {
        this.maxContentChars = maxContentChars;
    }

    public EmailContent extract(String id, byte[] eml) {
        var accumulator = new Accumulator();
        MimeMessage message;
        try (var stream = new ByteArrayInputStream(eml)) {
            // getInstance, not getDefaultInstance: the latter hands back a JVM-wide shared session
            // whose mail.mime.* parsing flags depend on whoever created it first.
            message = new MimeMessage(Session.getInstance(new Properties(), null), stream);
            collectHeaders(message, accumulator);
        } catch (MessagingException | IOException e) {
            LOG.warn("Failed to parse eml for MailHog message id={}", id, e);
            throw new MailhogMessageParseException(id, e);
        }

        try {
            collectParts(message, accumulator);
        } catch (MessagingException | IOException e) {
            // The MIME tree is malformed below the top level. Keep the headers and whatever parts
            // were collected rather than failing the whole message.
            LOG.warn("Failed to walk MIME parts for MailHog message id={}; returning partial content", id, e);
        }

        // One budget for everything decoded, spent in priority order: the bodies a caller usually
        // wants first, then the remaining parts. Capping each piece separately would let a message
        // with many parts return an arbitrary multiple of the configured limit.
        var budget = new Budget(maxContentChars);
        var textBody = budget.take(accumulator.textBody);
        var htmlBody = budget.take(accumulator.htmlBody);
        var otherParts = new ArrayList<EmailPart>(accumulator.otherParts.size());
        for (var part : accumulator.otherParts) {
            otherParts.add(new EmailPart(part.contentType(), budget.take(part.content())));
        }

        return new EmailContent(
                id,
                accumulator.headers,
                textBody,
                htmlBody,
                accumulator.attachments,
                otherParts,
                budget.truncated());
    }

    private void collectHeaders(MimeMessage message, Accumulator accumulator) throws MessagingException {
        var headers = message.getAllHeaders();
        while (headers.hasMoreElements()) {
            var header = headers.nextElement();
            accumulator.headers.add(new EmailHeader(header.getName(), headerValue(header.getValue())));
        }
    }

    /**
     * Unfolds continuation lines so one header stays one line, then decodes RFC 2047 encoded
     * words ({@code =?UTF-8?B?...?=}) so a non-ASCII Subject is as readable as the body beside it.
     */
    private String headerValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(MimeUtility.unfold(value));
        } catch (IOException e) {
            LOG.debug("Could not decode header value, using raw form", e);
            return value;
        }
    }

/**
 * Walks the MIME tree depth-first, keeping the first inline text/plain and text/html parts as
 * the bodies. Attachments and other non-text leaves are recorded as metadata, and other textual
 * leaves are recorded as an {@link EmailPart} so nothing disappears without a trace.
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
            accumulator.textBody = decodeText(part);
            return;
        }
        if (part.isMimeType("text/html") && accumulator.htmlBody == null) {
            accumulator.htmlBody = decodeText(part);
            return;
        }

        // Any other leaf: a DSN report (message/delivery-status), forwarded headers
        // (message/rfc822-headers), an inline image, or a second text part. Textual ones carry
        // their content — for a mocked bounce those are the Status and Diagnostic-Code fields that
        // are the entire reason to inspect the message.
        if (isTextual(part)) {
            accumulator.otherParts.add(new EmailPart(baseContentType(part), decodeText(part)));
        } else {
            accumulator.attachments.add(new EmailAttachment(
                    part.getFileName(), baseContentType(part), part.getSize()));
        }
    }

    private boolean isAttachment(Part part) throws MessagingException {
        return Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null;
    }

private boolean isTextual(Part part) throws MessagingException {
    return part.isMimeType("text/*")
            || part.isMimeType("message/delivery-status")
            || part.isMimeType("message/rfc822-headers");
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

    /**
     * Reads a part as text, containing any decoding failure to this part alone.
     * <p>
     * {@code getContent()} throws {@link java.io.UnsupportedEncodingException} for a charset the
     * JVM does not know, and returns a stream rather than a String for types with no registered
     * content handler (such as {@code message/delivery-status}). Both fall back to the
     * transfer-decoded bytes, which are already free of base64 and quoted-printable encoding.
     */
    private String decodeText(Part part) {
        try {
            if (part.getContent() instanceof String text) {
                return text;
            }
        } catch (MessagingException | IOException e) {
            LOG.debug("Content handler could not decode part, falling back to raw bytes", e);
        }
        try (var stream = part.getInputStream()) {
            // ISO-8859-1, not UTF-8: this path runs precisely because the declared charset was
            // unusable, so any guess is wrong. Latin-1 maps every byte to a character, keeping the
            // content recoverable, where UTF-8 would replace malformed sequences with U+FFFD.
            return readAll(stream, StandardCharsets.ISO_8859_1);
        } catch (MessagingException | IOException e) {
            LOG.warn("Could not read part content", e);
            return null;
        }
    }

    private String readAll(InputStream stream, Charset charset) throws IOException {
        return new String(stream.readAllBytes(), charset);
    }

    /**
     * A shared character allowance for everything decoded out of one message, so the total returned
     * is bounded no matter how many parts the message carries.
     */
    private static final class Budget {
        private int remaining;
        private boolean truncated;

        private Budget(int limit) {
            this.remaining = Math.max(limit, 0);
        }

        private String take(String text) {
            if (text == null || text.length() <= remaining) {
                remaining -= text == null ? 0 : text.length();
                return text;
            }
            var kept = text.substring(0, remaining);
            remaining = 0;
            truncated = true;
            return kept + TRUNCATION_MARKER;
        }

        private boolean truncated() {
            return truncated;
        }
    }

    /**
     * Mutable state for a single extraction. Held locally so the component stays stateless.
     */
    private static final class Accumulator {
        private final List<EmailHeader> headers = new ArrayList<>();
        private final List<EmailAttachment> attachments = new ArrayList<>();
        private final List<EmailPart> otherParts = new ArrayList<>();
        private String textBody;
        private String htmlBody;
    }
}
