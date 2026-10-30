package com.duotail.utils.email.mailhog;

import com.duotail.utils.email.mailhog.dto.EmailContent;
import com.duotail.utils.email.mailhog.dto.EmailHeader;
import com.duotail.utils.email.mailhog.dto.MailhogReplyRequest;
import com.duotail.utils.email.sender.EmailSendService;
import com.duotail.utils.email.sender.permission.PermissionException;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Builds a reply (or reply-all) to a message captured by MailHog and sends it through
 * {@link EmailSendService}, so the shared permission checks and sender apply. Reply headers —
 * recipients, {@code Re:} subject and {@code In-Reply-To}/{@code References} threading — come from
 * Jakarta Mail's {@link MimeMessage#reply(boolean)}.
 */
@Slf4j
@Service
public class MailhogReplyService {

    private static final String QUOTE_STYLE = "margin:0 0 0 .8ex;border-left:1px solid #ccc;padding-left:1ex";

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
        var eml = mailhogService.getMessageEml(id);
        var original = parse(id, eml, replier.getAddress());

        var reply = (MimeMessage) original.reply(replyAll);
        reply.setFrom(replier);
        new MimeMessageHelper(reply, StandardCharsets.UTF_8.name())
                .setText(request.getContent() + quote(emlContentExtractor.extract(id, eml)), true);

        LOG.info("Sending {} to MailHog message id={} [from: {} | to: {} | cc: {} ]",
                replyAll ? "reply-all" : "reply", id, replier,
                joinAddresses(reply.getRecipients(Message.RecipientType.TO)),
                joinAddresses(reply.getRecipients(Message.RecipientType.CC)));
        emailSendService.sendMimeMessage(reply);
    }

    /**
     * Parses the original with a session that knows the replier as "me": {@code mail.from} and
     * {@code mail.alternates} are how {@link MimeMessage#reply(boolean)} drops the replier from
     * reply-all recipients.
     */
    private MimeMessage parse(String id, byte[] eml, String replierAddress) {
        var properties = new Properties();
        properties.setProperty("mail.from", replierAddress);
        properties.setProperty("mail.alternates", replierAddress);
        try (var stream = new ByteArrayInputStream(eml)) {
            return new MimeMessage(Session.getInstance(properties, null), stream);
        } catch (MessagingException | IOException e) {
            LOG.warn("Failed to parse eml for MailHog message id={}", id, e);
            throw new MailhogMessageParseException(id, e);
        }
    }

    /**
     * Quotes the original below the reply: the HTML body when there is one, otherwise the text
     * body escaped for HTML. A message with neither gets no quote block at all.
     */
    private String quote(EmailContent content) {
        String quotedBody;
        if (content.htmlBody() != null) {
            quotedBody = content.htmlBody();
        } else if (content.textBody() != null) {
            quotedBody = HtmlUtils.htmlEscape(content.textBody()).replace("\r\n", "\n").replace("\n", "<br>");
        } else {
            return "";
        }

        // The extractor's headers are unfolded and RFC 2047-decoded, so a non-ASCII sender name reads correctly
        var from = HtmlUtils.htmlEscape(StringUtils.defaultString(header(content, "From")));
        var date = header(content, "Date");
        var attribution = date == null
                ? from + " wrote:"
                : "On " + HtmlUtils.htmlEscape(date) + ", " + from + " wrote:";

        return "<br><br><div>" + attribution + "</div>"
                + "<blockquote style=\"" + QUOTE_STYLE + "\">" + quotedBody + "</blockquote>";
    }

    private String header(EmailContent content, String name) {
        return content.headers().stream()
                .filter(header -> name.equalsIgnoreCase(header.name()))
                .map(EmailHeader::value)
                .findFirst()
                .orElse(null);
    }

    private String joinAddresses(Address[] addresses) {
        if (addresses == null) {
            return "";
        }
        return Arrays.stream(addresses).map(Address::toString).collect(Collectors.joining(","));
    }
}
