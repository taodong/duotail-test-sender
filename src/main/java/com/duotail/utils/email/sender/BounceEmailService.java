package com.duotail.utils.email.sender;

import com.duotail.utils.email.sender.permission.PermissionException;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MailDateFormat;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

/**
 * Builds and sends mocked bounce messages as RFC 3464 Delivery Status Notifications
 * ({@code multipart/report; report-type=delivery-status}). The generated message is handed to
 * {@link EmailSendService} so it reuses the shared permission-validation and send pipeline.
 */
@Slf4j
@Service
public class BounceEmailService {

    private final Properties properties = new Properties();
    private final EmailSendService emailSendService;
    private final Validator validator;
    private final String mailerDaemon;
    private final String defaultReportingMta;

    public BounceEmailService(EmailSendService emailSendService,
                              Validator validator,
                              @Value("${app.bounce.mailer-daemon}") String mailerDaemon,
                              @Value("${app.bounce.reporting-mta}") String defaultReportingMta) {
        this.emailSendService = emailSendService;
        this.validator = validator;
        this.mailerDaemon = mailerDaemon;
        this.defaultReportingMta = defaultReportingMta;
    }

    public void sendBounce(BounceRequest request) throws MessagingException, PermissionException {
        // Guard callers that skip @Valid (e.g. the MCP tool) so invalid input fails cleanly
        // rather than NPE-ing while the DSN is built.
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }

        var statusCode = StringUtils.isNotBlank(request.getStatusCode())
                ? request.getStatusCode()
                : request.getBounceType().getDefaultStatusCode();
        var diagnostic = StringUtils.isNotBlank(request.getDiagnosticText())
                ? request.getDiagnosticText()
                : BounceDiagnostics.forStatus(statusCode, request.getBounceType());
        var reportingMta = StringUtils.isNotBlank(request.getReportingMta())
                ? request.getReportingMta()
                : defaultReportingMta;
        var originalMessageId = StringUtils.isNotBlank(request.getOriginalMessageId())
                ? request.getOriginalMessageId()
                : "<" + UUID.randomUUID() + "@mail.duotail.test>";

        var message = buildBounceMessage(request, statusCode, diagnostic, reportingMta, originalMessageId);

        LOG.info("Sending {} bounce [to original sender: {} | failed recipient: {} | status: {} ]",
                request.getBounceType(), request.getOriginalFrom(), request.getOriginalTo(), statusCode);
        emailSendService.sendBounceMimeMessage(message);
    }

    private MimeMessage buildBounceMessage(BounceRequest request,
                                           String statusCode,
                                           String diagnostic,
                                           String reportingMta,
                                           String originalMessageId) throws MessagingException {
        var bounceType = request.getBounceType();
        var message = new MimeMessage(Session.getDefaultInstance(properties, null));
        message.setFrom(new InternetAddress(mailerDaemon));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(request.getOriginalFrom()));
        message.setSubject("Delivery Status Notification (" + bounceType.getSubjectSuffix() + ")");
        message.setHeader("Auto-Submitted", "auto-generated");
        message.setHeader("In-Reply-To", originalMessageId);
        message.setHeader("References", originalMessageId);

        var report = new MimeMultipart("report");
        report.addBodyPart(humanReadablePart(request, statusCode, diagnostic));
        report.addBodyPart(deliveryStatusPart(request, statusCode, diagnostic, reportingMta));
        report.addBodyPart(originalHeadersPart(request, originalMessageId, reportingMta));

        message.setContent(report);
        // MimeMultipart("report") yields "multipart/report" but not the required report-type parameter.
        message.setHeader("Content-Type", report.getContentType() + "; report-type=delivery-status");
        message.saveChanges();
        return message;
    }

    private MimeBodyPart humanReadablePart(BounceRequest request,
                                           String statusCode,
                                           String diagnostic) throws MessagingException {
        var text = "This is an automatically generated Delivery Status Notification.\r\n\r\n"
                + "Delivery to the following recipient " + failureVerb(request.getBounceType()) + ":\r\n\r\n"
                + "    " + request.getOriginalTo() + "\r\n\r\n"
                + "Status: " + statusCode + "\r\n"
                + "Reason: " + diagnostic + "\r\n\r\n"
                + "Original subject: " + request.getOriginalSubject() + "\r\n";
        var part = new MimeBodyPart();
        part.setText(text, "utf-8");
        return part;
    }

    private MimeBodyPart deliveryStatusPart(BounceRequest request,
                                            String statusCode,
                                            String diagnostic,
                                            String reportingMta) throws MessagingException {
        var deliveryStatus = "Reporting-MTA: " + StringUtils.replaceChars(reportingMta, "\r\n", "  ") + "\r\n"
                + "\r\n"
                + "Final-Recipient: rfc822; " + StringUtils.replaceChars(request.getOriginalTo(), "\r\n", "  ") + "\r\n"
                + "Action: " + request.getBounceType().getAction() + "\r\n"
                + "Status: " + StringUtils.replaceChars(statusCode, "\r\n", "  ") + "\r\n"
                + "Diagnostic-Code: " + StringUtils.replaceChars(diagnostic, "\r\n", "  ") + "\r\n";
        var part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(new ByteArrayDataSource(
                deliveryStatus.getBytes(StandardCharsets.UTF_8), "message/delivery-status")));
        return part;
    }

    private MimeBodyPart originalHeadersPart(BounceRequest request,
                                             String originalMessageId,
                                             String reportingMta) throws MessagingException {
        // A synthetic Received header so bounce classifiers that verify origin (e.g. Haraka's
        // non_local_msgid check) find the trace headers they expect; the host is taken from the
        // Reporting-MTA, dropping any "type;" prefix (e.g. "dns; mail.duotail.test").
        var receivedHost = StringUtils.defaultIfBlank(
                StringUtils.substringAfter(reportingMta, ";").trim(), reportingMta);
        var receivedLine = "Received: from " + StringUtils.replaceChars(request.getOriginalTo(), "\r\n", "  ")
                + " by " + StringUtils.replaceChars(receivedHost, "\r\n", "  ")
                + " with ESMTP id " + UUID.randomUUID()
                + "; " + new MailDateFormat().format(new Date()) + "\r\n";
        var originalHeaders = receivedLine
                + "From: " + StringUtils.replaceChars(request.getOriginalFrom(), "\r\n", "  ") + "\r\n"
                + "To: " + StringUtils.replaceChars(request.getOriginalTo(), "\r\n", "  ") + "\r\n"
                + "Subject: " + StringUtils.replaceChars(request.getOriginalSubject(), "\r\n", "  ") + "\r\n"
                + "Message-ID: " + StringUtils.replaceChars(originalMessageId, "\r\n", "  ") + "\r\n"
                + "Date: " + new MailDateFormat().format(new Date()) + "\r\n";
        var part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(new ByteArrayDataSource(
                originalHeaders.getBytes(StandardCharsets.UTF_8), "message/rfc822-headers")));
        return part;
    }

    private String failureVerb(BounceType bounceType) {
        return bounceType == BounceType.HARD ? "failed permanently" : "was delayed";
    }
}
