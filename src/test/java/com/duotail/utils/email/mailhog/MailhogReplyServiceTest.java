package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.MailhogReplyRequest;
import com.duotail.utils.email.sender.EmailSendService;
import com.duotail.utils.email.sender.permission.PermissionException;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailhogReplyServiceTest {

    private static final String ID = "abc123@mailhog.example";

    private static final String HTML_EML = """
            From: Alice <alice@example.com>\r
            To: bob@example.com, carol@example.com\r
            Cc: dave@example.com\r
            Bcc: eve@example.com\r
            Subject: Project update\r
            Date: Thu, 29 Oct 2026 10:00:00 +0000\r
            Message-ID: <original@example.com>\r
            References: <earlier@example.com>\r
            MIME-Version: 1.0\r
            Content-Type: text/html; charset=UTF-8\r
            \r
            <p>Original body</p>\r
            """;

    @Mock
    private MailhogService mailhogService;

    @Mock
    private EmailSendService emailSendService;

    private MailhogReplyService mailhogReplyService;

    @BeforeEach
    void setUp() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        mailhogReplyService = new MailhogReplyService(
                mailhogService, new EmlContentExtractor(100_000), emailSendService, validator);
    }

    @Test
    void replyAddressesOriginalSenderOnly() throws Exception {
        givenEml(HTML_EML);

        mailhogReplyService.reply(ID, request("bob@example.com"), false);

        var reply = sentMessage();
        assertEquals(List.of("alice@example.com"), addresses(reply.getRecipients(Message.RecipientType.TO)));
        assertNull(reply.getRecipients(Message.RecipientType.CC));
        assertEquals("bob@example.com", ((InternetAddress) reply.getFrom()[0]).getAddress());
    }

    @Test
    void replyPrefersReplyToOverFrom() throws Exception {
        givenEml("""
                From: alice@example.com\r
                Reply-To: alice-replies@example.com\r
                To: bob@example.com\r
                Subject: Hi\r
                \r
                body\r
                """);

        mailhogReplyService.reply(ID, request("bob@example.com"), false);

        assertEquals(List.of("alice-replies@example.com"),
                addresses(sentMessage().getRecipients(Message.RecipientType.TO)));
    }

    @Test
    void replyAllIncludesOtherRecipientsExcludingReplierAndBcc() throws Exception {
        givenEml(HTML_EML);

        mailhogReplyService.reply(ID, request("Bob <bob@example.com>"), true);

        var reply = sentMessage();
        assertEquals(List.of("alice@example.com", "carol@example.com"),
                addresses(reply.getRecipients(Message.RecipientType.TO)));
        assertEquals(List.of("dave@example.com"), addresses(reply.getRecipients(Message.RecipientType.CC)));
        assertNull(reply.getRecipients(Message.RecipientType.BCC));
    }

    @Test
    void replySetsSubjectAndThreadingHeaders() throws Exception {
        givenEml(HTML_EML);

        mailhogReplyService.reply(ID, request("bob@example.com"), false);

        var reply = sentMessage();
        assertEquals("Re: Project update", reply.getSubject());
        assertEquals("<original@example.com>", reply.getHeader("In-Reply-To", null));
        var references = reply.getHeader("References", " ");
        assertTrue(references.contains("<earlier@example.com>"), references);
        assertTrue(references.contains("<original@example.com>"), references);
    }

    @Test
    void replyDoesNotStackRePrefix() throws Exception {
        givenEml("""
                From: alice@example.com\r
                To: bob@example.com\r
                Subject: Re: Project update\r
                \r
                body\r
                """);

        mailhogReplyService.reply(ID, request("bob@example.com"), false);

        assertEquals("Re: Project update", sentMessage().getSubject());
    }

    @Test
    void replyBodyHasCallerContentThenQuotedHtmlOriginal() throws Exception {
        givenEml(HTML_EML);

        mailhogReplyService.reply(ID, request("bob@example.com"), false);

        var body = (String) sentMessage().getContent();
        assertTrue(body.startsWith("<p>Thanks!</p>"), body);
        assertTrue(body.contains("On Thu, 29 Oct 2026 10:00:00 +0000, Alice &lt;alice@example.com&gt; wrote:"), body);
        assertTrue(body.contains("<blockquote"), body);
        assertTrue(body.contains("<p>Original body</p>"), body);
    }

    @Test
    void replyQuotesTextOriginalEscapedWithLineBreaks() throws Exception {
        givenEml("""
                From: alice@example.com\r
                To: bob@example.com\r
                Subject: Hi\r
                Content-Type: text/plain; charset=UTF-8\r
                \r
                a < b\r
                second line\r
                """);

        mailhogReplyService.reply(ID, request("bob@example.com"), false);

        var body = (String) sentMessage().getContent();
        assertTrue(body.contains("a &lt; b<br>second line"), body);
        assertTrue(body.contains("alice@example.com wrote:"), body);
        assertFalse(body.contains("On "), "no Date header, so no date in the attribution: " + body);
    }

    @Test
    void replyOmitsQuoteWhenOriginalHasNoBody() throws Exception {
        givenEml("""
                From: alice@example.com\r
                To: bob@example.com\r
                Subject: Hi\r
                Content-Type: multipart/mixed; boundary="b"\r
                \r
                --b\r
                Content-Type: application/pdf\r
                Content-Disposition: attachment; filename="a.pdf"\r
                \r
                pdf\r
                --b--\r
                """);

        mailhogReplyService.reply(ID, request("bob@example.com"), false);

        assertEquals("<p>Thanks!</p>", sentMessage().getContent());
    }

    @Test
    void replyToOwnMessageDoesNotAddressTheReplier() throws Exception {
        givenEml(HTML_EML);

        mailhogReplyService.reply(ID, request("alice@example.com"), true);

        var reply = sentMessage();
        assertEquals(List.of("bob@example.com", "carol@example.com"),
                addresses(reply.getRecipients(Message.RecipientType.TO)));
        assertEquals(List.of("dave@example.com"), addresses(reply.getRecipients(Message.RecipientType.CC)));
    }

    @Test
    void replyFailsWithoutSendingWhenNobodyIsLeftToReplyTo() {
        givenEml("""
                From: alice@example.com\r
                To: alice@example.com\r
                Subject: Note to self\r
                \r
                body\r
                """);

        assertThrows(MailhogReplyNoRecipientsException.class,
                () -> mailhogReplyService.reply(ID, request("alice@example.com"), true));

        verifyNoInteractions(emailSendService);
    }

    @Test
    void replyFailsWhenOriginalHasNoSender() {
        givenEml("""
                To: bob@example.com\r
                Subject: Anonymous\r
                \r
                body\r
                """);

        assertThrows(MailhogReplyNoRecipientsException.class,
                () -> mailhogReplyService.reply(ID, request("bob@example.com"), false));

        verifyNoInteractions(emailSendService);
    }

    @Test
    void replyDoesNotStackRePrefixOnEncodedOrUppercaseSubjects() throws Exception {
        givenEml("""
                From: alice@example.com\r
                To: bob@example.com\r
                Subject: =?UTF-8?B?UmU6IOS9oOWlvQ==?=\r
                \r
                body\r
                """);
        mailhogReplyService.reply(ID, request("bob@example.com"), false);
        assertEquals("Re: \u4f60\u597d", sentMessage().getSubject());

        org.mockito.Mockito.clearInvocations(emailSendService);
        givenEml("""
                From: alice@example.com\r
                To: bob@example.com\r
                Subject: RE:foo\r
                \r
                body\r
                """);
        mailhogReplyService.reply(ID, request("bob@example.com"), false);
        assertEquals("RE:foo", sentMessage().getSubject());
    }

    @Test
    void replyQuotesOnlyBodyContentOfAnHtmlDocument() throws Exception {
        givenEml("""
                From: =?UTF-8?B?QWxpY8Op?= <alice@example.com>\r
                To: bob@example.com\r
                Subject: Styled\r
                Content-Type: text/html; charset=UTF-8\r
                \r
                <!DOCTYPE html><html><head><style>p{color:#fff}</style></head>\r
                <body class="x"><style>.y{}</style><p>Kept</p><img src="cid:logo@x"><img src="https://x/a.png"></body></html>\r
                """);

        mailhogReplyService.reply(ID, request("bob@example.com"), false);

        var body = (String) sentMessage().getContent();
        assertTrue(body.contains("<p>Kept</p>"), body);
        assertTrue(body.contains("<img src=\"https://x/a.png\">"), body);
        assertTrue(body.contains("&quot;Alic\u00e9&quot; &lt;alice@example.com&gt; wrote:"), body);
        for (var removed : List.of("<style", "<head", "<html", "<body", "DOCTYPE", "cid:")) {
            assertFalse(body.contains(removed), removed + " must be removed: " + body);
        }
    }

    @Test
    void replyQuotesLargeOriginalWhole() throws Exception {
        mailhogReplyService = new MailhogReplyService(mailhogService, new EmlContentExtractor(10), emailSendService,
                Validation.buildDefaultValidatorFactory().getValidator());
        givenEml(HTML_EML);

        mailhogReplyService.reply(ID, request("bob@example.com"), false);

        var body = (String) sentMessage().getContent();
        assertTrue(body.contains("<p>Original body</p>"), body);
        assertFalse(body.contains("[truncated]"), body);
    }

    @Test
    void replyKeepsRawUtf8DisplayNameIntact() throws Exception {
        // POST /api/email writes non-ASCII display names as raw UTF-8 rather than RFC 2047
        givenEml("""
                From: "Alic\u00e9" <alice@example.com>\r
                To: bob@example.com\r
                Subject: Hi\r
                Content-Type: text/plain; charset=UTF-8\r
                \r
                body\r
                """);

        mailhogReplyService.reply(ID, request("bob@example.com"), false);

        var reply = sentMessage();
        assertEquals("Alic\u00e9", ((InternetAddress) reply.getRecipients(Message.RecipientType.TO)[0]).getPersonal());
        assertTrue(((String) reply.getContent()).contains("&quot;Alic\u00e9&quot; &lt;alice@example.com&gt; wrote:"));
    }

    @Test
    void replyRejectsInvalidRequestWithoutSending() {
        var request = new MailhogReplyRequest();
        request.setFrom("not an address");
        request.setContent("");

        assertThrows(ConstraintViolationException.class, () -> mailhogReplyService.reply(ID, request, true));

        verifyNoInteractions(mailhogService, emailSendService);
    }

    @Test
    void replyPropagatesPermissionViolation() throws Exception {
        givenEml(HTML_EML);
        doThrow(new PermissionException("Sender is not authorized: bob@example.com"))
                .when(emailSendService).sendMimeMessage(any(MimeMessage.class));

        assertThrows(PermissionException.class,
                () -> mailhogReplyService.reply(ID, request("bob@example.com"), false));
    }

    @Test
    void replyPropagatesMessageNotFound() {
        when(mailhogService.getMessageEml(ID)).thenThrow(new MailhogMessageNotFoundException(ID));

        assertThrows(MailhogMessageNotFoundException.class,
                () -> mailhogReplyService.reply(ID, request("bob@example.com"), false));

        verifyNoInteractions(emailSendService);
    }

    private void givenEml(String eml) {
        when(mailhogService.getMessageEml(ID)).thenReturn(eml.getBytes(StandardCharsets.UTF_8));
    }

    private MailhogReplyRequest request(String from) {
        var request = new MailhogReplyRequest();
        request.setFrom(from);
        request.setContent("<p>Thanks!</p>");
        return request;
    }

    private MimeMessage sentMessage() throws Exception {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(emailSendService).sendMimeMessage(captor.capture());
        var message = captor.getValue();
        // Mirror what the real sender does before transport, so getContent() reflects the set body
        message.saveChanges();
        return message;
    }

    private List<String> addresses(Address[] addresses) {
        return Arrays.stream(addresses).map(address -> ((InternetAddress) address).getAddress()).toList();
    }
}
