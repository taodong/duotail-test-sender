package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.EmailContent;
import com.duotail.utils.email.mailhog.dto.EmailHeader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EmlContentExtractorTest {

    private static final int MAX_BODY_CHARS = 100_000;

    private final EmlContentExtractor extractor = new EmlContentExtractor(MAX_BODY_CHARS);

    /**
     * The regression behind this feature: bug-reports/1-getmailhogmessage-id-mismatch.md.
     * A quoted-printable body encodes '=' as '=3D' and folds long lines with '=' soft breaks,
     * which splits an activation URL across lines. The decoded body must restore it exactly.
     */
    @Test
    void decodesQuotedPrintableSoActivationLinkSurvivesIntact() {
        var eml = eml(
                "Subject: New Email Confirmation",
                "From: donotreply@duotail.com",
                "To: dwtest.user7@enduser1.com",
                "MIME-Version: 1.0",
                "Content-Type: text/html; charset=UTF-8",
                "Content-Transfer-Encoding: quoted-printable",
                "",
                "<html><body>Please confirm: <a href=3D\"http://localhost:30080/confirm-ema=",
                "il?token=3Da75e7828-9dc7-4661-b8a2-92b0b374e2a9\">Confirm</a></body></html>");

        var content = extractor.extract("abc123", eml);

        assertTrue(content.htmlBody().contains(
                        "http://localhost:30080/confirm-email?token=a75e7828-9dc7-4661-b8a2-92b0b374e2a9"),
                "activation URL must be reassembled and unescaped, was: " + content.htmlBody());
        assertFalse(content.htmlBody().contains("=3D"), "quoted-printable escapes must be decoded");
        assertFalse(content.htmlBody().contains("=\r\n"), "soft line breaks must be removed");
    }

    @Test
    void extractsAllHeadersInOrderPreservingDuplicates() {
        var eml = eml(
                "Received: from a.example by b.example",
                "Received: from c.example by d.example",
                "Subject: Test Subject",
                "From: sender@example.com",
                "",
                "body");

        var content = extractor.extract("abc123", eml);

        assertEquals("abc123", content.id());
        assertEquals(2, content.headers().stream().filter(h -> h.name().equals("Received")).count(),
                "repeated headers must not be collapsed");
        assertEquals("Received", content.headers().getFirst().name());
        assertTrue(content.headers().stream()
                .anyMatch(h -> h.name().equals("Subject") && h.value().equals("Test Subject")));
    }

    @Test
    void extractsBothPartsOfMultipartAlternative() {
        var eml = eml(
                "Subject: Multipart",
                "MIME-Version: 1.0",
                "Content-Type: multipart/alternative; boundary=\"BOUND\"",
                "",
                "--BOUND",
                "Content-Type: text/plain; charset=UTF-8",
                "",
                "plain version",
                "--BOUND",
                "Content-Type: text/html; charset=UTF-8",
                "",
                "<p>html version</p>",
                "--BOUND--");

        var content = extractor.extract("abc123", eml);

        assertTrue(content.textBody().contains("plain version"));
        assertTrue(content.htmlBody().contains("<p>html version</p>"));
        assertFalse(content.truncated());
    }

    @Test
    void decodesBase64Body() {
        var html = "<html><body>base64 payload</body></html>";
        var eml = eml(
                "Subject: Base64",
                "MIME-Version: 1.0",
                "Content-Type: text/html; charset=UTF-8",
                "Content-Transfer-Encoding: base64",
                "",
                Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8)));

        var content = extractor.extract("abc123", eml);

        assertEquals(html, content.htmlBody());
        assertNull(content.textBody());
    }

    @Test
    void leavesHtmlBodyNullWhenMessageIsPlainTextOnly() {
        var eml = eml(
                "Subject: Plain",
                "Content-Type: text/plain; charset=UTF-8",
                "",
                "just text");

        var content = extractor.extract("abc123", eml);

        assertEquals("just text", content.textBody().trim());
        assertNull(content.htmlBody());
        assertTrue(content.attachments().isEmpty());
    }

    @Test
    void recordsAttachmentAsMetadataWithoutInliningContent() {
        var attachmentBody = Base64.getEncoder()
                .encodeToString("file contents here".getBytes(StandardCharsets.UTF_8));
        var eml = eml(
                "Subject: With Attachment",
                "MIME-Version: 1.0",
                "Content-Type: multipart/mixed; boundary=\"BOUND\"",
                "",
                "--BOUND",
                "Content-Type: text/plain; charset=UTF-8",
                "",
                "see attached",
                "--BOUND",
                "Content-Type: application/pdf; name=\"invoice.pdf\"",
                "Content-Transfer-Encoding: base64",
                "Content-Disposition: attachment; filename=\"invoice.pdf\"",
                "",
                attachmentBody,
                "--BOUND--");

        var content = extractor.extract("abc123", eml);

        assertEquals(1, content.attachments().size());
        var attachment = content.attachments().getFirst();
        assertEquals("invoice.pdf", attachment.filename());
        assertEquals("application/pdf", attachment.contentType());
        assertTrue(content.textBody().contains("see attached"));
        assertFalse(content.textBody().contains("file contents here"),
                "attachment content must never be inlined into a body");
    }

    @Test
    void truncatesBodyBeyondConfiguredCapAndFlagsIt() {
        var smallCapExtractor = new EmlContentExtractor(10);
        var eml = eml(
                "Subject: Long",
                "Content-Type: text/plain; charset=UTF-8",
                "",
                "0123456789ABCDEFGHIJ");

        var content = smallCapExtractor.extract("abc123", eml);

        assertTrue(content.truncated());
        assertTrue(content.textBody().startsWith("0123456789"));
        assertTrue(content.textBody().contains("[truncated]"));
        assertFalse(content.textBody().contains("ABCDEFGHIJ"));
    }

    /**
     * The cap is an allowance for the whole message, not for each piece: a message with many parts
     * must not return an arbitrary multiple of the configured limit.
     */
    @Test
    void sharesOneAllowanceAcrossBodiesAndParts() {
        var smallCapExtractor = new EmlContentExtractor(30);
        var eml = eml(
                "Subject: Many Parts",
                "MIME-Version: 1.0",
                "Content-Type: multipart/report; report-type=delivery-status; boundary=\"BOUND\"",
                "",
                "--BOUND",
                "Content-Type: text/plain; charset=UTF-8",
                "",
                "AAAAAAAAAAAAAAAAAAAA",
                "--BOUND",
                "Content-Type: message/delivery-status",
                "",
                "BBBBBBBBBBBBBBBBBBBB",
                "--BOUND",
                "Content-Type: message/rfc822-headers",
                "",
                "CCCCCCCCCCCCCCCCCCCC",
                "--BOUND--");

        var content = smallCapExtractor.extract("abc123", eml);

        assertTrue(content.truncated());
        var decoded = content.textBody().length()
                + content.otherParts().stream()
                        .map(p -> p.content() == null ? "" : p.content())
                        .mapToInt(String::length)
                        .sum();
        var markerAllowance = content.otherParts().size() + 1;
        assertTrue(decoded <= 30 + markerAllowance * "\n...[truncated]".length(),
                "total decoded content must stay within one allowance, was " + decoded);
        assertTrue(content.textBody().startsWith("AAAA"), "bodies get the allowance first");
    }

    @Test
    void fallbackDecodingPreservesBytesRatherThanReplacingThem() {
        // 0xE9 is 'é' in latin-1 but an invalid UTF-8 sequence on its own; under an unusable
        // charset the byte must survive rather than becoming U+FFFD
        var header = String.join("\r\n",
                "Subject: Latin1",
                "Content-Type: text/plain; charset=\"unknown-8bit\"",
                "",
                "caf");
        var bytes = new byte[header.getBytes(StandardCharsets.UTF_8).length + 1];
        System.arraycopy(header.getBytes(StandardCharsets.UTF_8), 0, bytes, 0, bytes.length - 1);
        bytes[bytes.length - 1] = (byte) 0xE9;

        var content = extractor.extract("abc123", bytes);

        assertEquals("café", content.textBody());
        assertFalse(content.textBody().contains("�"), "bytes must not be replaced");
    }

    /**
     * BounceEmailService builds DSN parts with a bare DataHandler — no filename and no
     * Content-Disposition — so they are neither attachments nor text bodies. They must still come
     * back: Status, Action and Diagnostic-Code are the whole reason to inspect a bounce.
     */
    @Test
    void keepsDeliveryStatusPartsOfADsnBounce() {
        var eml = eml(
                "Subject: Undelivered Mail Returned to Sender",
                "MIME-Version: 1.0",
                "Content-Type: multipart/report; report-type=delivery-status; boundary=\"BOUND\"",
                "",
                "--BOUND",
                "Content-Type: text/plain; charset=UTF-8",
                "",
                "Your message failed permanently.",
                "--BOUND",
                "Content-Type: message/delivery-status",
                "",
                "Reporting-MTA: dns; mail.duotail.test",
                "",
                "Final-Recipient: rfc822; bounce@enduser1.com",
                "Action: failed",
                "Status: 5.1.1",
                "Diagnostic-Code: smtp; 550 5.1.1 User unknown",
                "--BOUND",
                "Content-Type: message/rfc822-headers",
                "",
                "From: sender@duotail.com",
                "Subject: Original Subject",
                "--BOUND--");

        var content = extractor.extract("abc123", eml);

        assertTrue(content.textBody().contains("failed permanently"));
        assertEquals(2, content.otherParts().size(), "both DSN parts must be kept");

        var deliveryStatus = content.otherParts().getFirst();
        assertEquals("message/delivery-status", deliveryStatus.contentType());
        assertTrue(deliveryStatus.content().contains("Status: 5.1.1"));
        assertTrue(deliveryStatus.content().contains("Action: failed"));
        assertTrue(deliveryStatus.content().contains("Diagnostic-Code: smtp; 550 5.1.1 User unknown"));

        var originalHeaders = content.otherParts().get(1);
        assertEquals("message/rfc822-headers", originalHeaders.contentType());
        assertTrue(originalHeaders.content().contains("Subject: Original Subject"));
    }

    @Test
    void keepsHeadersAndOtherPartsWhenOnePartHasAnUnknownCharset() {
        var eml = eml(
                "Subject: Mixed Charsets",
                "MIME-Version: 1.0",
                "Content-Type: multipart/alternative; boundary=\"BOUND\"",
                "",
                "--BOUND",
                "Content-Type: text/plain; charset=\"unknown-8bit\"",
                "",
                "body with an unknown charset",
                "--BOUND",
                "Content-Type: text/html; charset=UTF-8",
                "",
                "<p>html still readable</p>",
                "--BOUND--");

        var content = extractor.extract("abc123", eml);

        assertTrue(content.headers().stream()
                        .anyMatch(h -> h.name().equals("Subject") && h.value().equals("Mixed Charsets")),
                "an undecodable part must not cost the caller the headers");
        assertTrue(content.htmlBody().contains("html still readable"),
                "an undecodable part must not cost the caller the other parts");
        assertTrue(content.textBody().contains("body with an unknown charset"),
                "the undecodable part falls back to its transfer-decoded bytes");
    }

    @Test
    void decodesRfc2047EncodedHeaderValues() {
        var eml = eml(
                "Subject: =?UTF-8?B?W1Rlc3RdIEJlc3TDpHRpZ3VuZw==?=",
                "From: sender@example.com",
                "Content-Type: text/plain; charset=UTF-8",
                "",
                "body");

        var content = extractor.extract("abc123", eml);

        assertEquals("[Test] Bestätigung", headerValue(content, "Subject"));
    }

    @Test
    void unfoldsHeadersSoEachOccupiesOneLine() {
        var eml = eml(
                "Received: from mac.lan by mailhog.example (MailHog)",
                "          id abc123; Mon, 17 Aug 2026 06:53:03 +0000",
                "Subject: Folded Header",
                "",
                "body");

        var content = extractor.extract("abc123", eml);

        var received = headerValue(content, "Received");
        assertFalse(received.contains("\n"), "folded header must not keep its line break: " + received);
        assertFalse(received.contains("\r"), "folded header must not keep its line break: " + received);
        assertTrue(received.contains("mailhog.example"));
        assertTrue(received.contains("id abc123"));
    }

    @Test
    void handlesMessageWithNoBodyParts() {
        var eml = eml(
                "Subject: Headers Only",
                "Content-Type: application/octet-stream",
                "",
                "");

        var content = extractor.extract("abc123", eml);

        assertNull(content.textBody());
        assertNull(content.htmlBody());
        assertFalse(content.headers().isEmpty());
    }

    private byte[] eml(String... lines) {
        return String.join("\r\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    private String headerValue(EmailContent content, String name) {
        return content.headers().stream()
                .filter(h -> h.name().equals(name))
                .map(EmailHeader::value)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + name + " header"));
    }
}
