package com.duotail.utils.email.mailhog;

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
}
