package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.MailhogReplyRequest;
import com.duotail.utils.email.sender.EmailSendService;
import com.duotail.utils.email.sender.permission.PermissionException;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds a reply (or reply-all) to a message captured by MailHog and sends it through
 * {@link EmailSendService}, so the shared permission checks and sender apply. Recipients and
 * {@code In-Reply-To}/{@code References} threading come from Jakarta Mail's
 * {@link MimeMessage#reply(boolean)}; the replier's removal and the subject are handled here.
 */
@Slf4j
@Service
public class MailhogReplyService {

    private static final String QUOTE_STYLE = "margin:0 0 0 .8ex;border-left:1px solid #ccc;padding-left:1ex";

    private static final Pattern RE_PREFIX = Pattern.compile("^\\s*re\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern BODY_CONTENT = Pattern.compile(
            "<body\\b[^>]*>(.*?)(?:</body\\s*>|\\z)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    // Elements that must not leak into the reply: global styles and scripts, plus the document wrapper
    private static final Pattern NON_CONTENT_BLOCKS = Pattern.compile(
            "<(head|style|script)\\b[^>]*>.*?</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DOCUMENT_TAGS = Pattern.compile(
            "<!DOCTYPE[^>]*>|</?(html|body)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    // Inline images reference MIME parts that are not carried into the reply, so they would only render broken
    private static final Pattern CID_IMAGES = Pattern.compile(
            "<img\\b[^>]*\\bsrc\\s*=\\s*[\"']?cid:[^>]*>", Pattern.CASE_INSENSITIVE);

    private final MailhogService mailhogService;
    private final EmlContentExtractor emlContentExtractor;
    private final EmailSendService emailSendService;
    private final Validator validator;

    public MailhogReplyService(MailhogService mailhogService,
                               EmlContentExtractor emlContentExtractor,
                               EmailSendService emailSendService,
                               Validator validator) {
        this.mailhogService = mailhogService;
        this.emlContentExtractor = emlContentExtractor;
        this.emailSendService = emailSendService;
        this.validator = validator;
    }

    public void reply(String id, MailhogReplyRequest request, boolean replyAll)
            throws MessagingException, PermissionException {
        // Guard callers that skip @Valid (e.g. the MCP tool) so invalid input fails cleanly.
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        var replier = new InternetAddress(request.getFrom(), true);
        var original = parse(id, mailhogService.getMessageEml(id), replier.getAddress());

        var reply = (MimeMessage) original.reply(replyAll);
        removeReplier(id, reply, replier);
        reply.setSubject(replySubject(original.getSubject()), StandardCharsets.UTF_8.name());
        reply.setFrom(replier);
        new MimeMessageHelper(reply, StandardCharsets.UTF_8.name())
                .setText(request.getContent() + quote(id, original), true);

        emailSendService.sendMimeMessage(reply);
    }

    /**
     * Parses the original with a session whose {@code mail.from} is the replier, which is how
     * {@link MimeMessage#reply(boolean)} recognises "me" among the original To/Cc.
     */
    private MimeMessage parse(String id, byte[] eml, String replierAddress) {
        var properties = new Properties();
        properties.setProperty("mail.from", replierAddress);
        try (var stream = new ByteArrayInputStream(eml)) {
            return new MimeMessage(Session.getInstance(properties, null), stream);
        } catch (MessagingException | IOException e) {
            LOG.warn("Failed to parse eml for MailHog message id={}", id, e);
            throw new MailhogMessageParseException(id, e);
        }
    }

    /**
     * {@code reply()} drops the replier only from the original To/Cc, never from the address it takes
     * from Reply-To/From, so replying to one's own message would otherwise mail oneself.
     */
    private void removeReplier(String id, MimeMessage reply, InternetAddress replier) throws MessagingException {
        var to = without(reply.getRecipients(Message.RecipientType.TO), replier);
        var cc = without(reply.getRecipients(Message.RecipientType.CC), replier);
        if (to.length == 0) {
            if (cc.length == 0) {
                throw new MailhogReplyNoRecipientsException(id);
            }
            to = cc;
            cc = new Address[0];
        }
        reply.setRecipients(Message.RecipientType.TO, to);
        reply.setRecipients(Message.RecipientType.CC, cc.length == 0 ? null : cc);
    }

    private Address[] without(Address[] addresses, InternetAddress replier) {
        if (addresses == null) {
            return new Address[0];
        }
        return Arrays.stream(addresses)
                .filter(address -> !(address instanceof InternetAddress internet)
                        || !replier.getAddress().equalsIgnoreCase(internet.getAddress()))
                .toArray(Address[]::new);
    }

    /**
     * {@code reply()} checks the raw, still-encoded subject for an exact {@code "Re: "}, so an
     * encoded or {@code RE:} subject would gain a second prefix. Check the decoded subject instead.
     */
    private String replySubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return "Re:";
        }
        return RE_PREFIX.matcher(subject).find() ? subject : "Re: " + subject;
    }

    /**
     * Quotes the original below the reply: the HTML body reduced to embeddable content when there
     * is one, otherwise the text body escaped for HTML. A message with neither gets no quote at all.
     */
    private String quote(String id, MimeMessage original) throws MessagingException {
        var bodies = emlContentExtractor.extractBodies(id, original);
        String quotedBody;
        if (bodies.htmlBody() != null) {
            quotedBody = embeddable(bodies.htmlBody());
        } else if (bodies.textBody() != null) {
            quotedBody = escape(bodies.textBody()).replace("\r\n", "\n").replace("\n", "<br>");
        } else {
            return "";
        }

        var from = escape(unicodeAddresses(original.getFrom()));
        var date = original.getHeader("Date", null);
        var attribution = date == null
                ? from + " wrote:"
                : "On " + escape(MimeUtility.unfold(date)) + ", " + from + " wrote:";

        return "<br><br><div>" + attribution + "</div>"
                + "<blockquote style=\"" + QUOTE_STYLE + "\">" + quotedBody + "</blockquote>";
    }

    /**
     * Keeps only the body content of an HTML document, so the original's head, styles and scripts
     * cannot restyle or run inside the reply.
     */
    private String embeddable(String html) {
        var body = BODY_CONTENT.matcher(html);
        var content = body.find() ? body.group(1) : html;
        content = NON_CONTENT_BLOCKS.matcher(content).replaceAll("");
        content = DOCUMENT_TAGS.matcher(content).replaceAll("");
        return CID_IMAGES.matcher(content).replaceAll("");
    }

    /**
     * Escapes only HTML-special characters; the body is UTF-8, so non-ASCII text stays readable as is.
     */
    private String escape(String text) {
        return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
    }

    private String unicodeAddresses(Address[] addresses) {
        if (addresses == null) {
            return "";
        }
        return Arrays.stream(addresses)
                .map(address -> address instanceof InternetAddress internet ? internet.toUnicodeString() : address.toString())
                .collect(Collectors.joining(", "));
    }
}
