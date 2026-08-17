package com.duotail.utils.email.mailhog.dto;

import java.util.List;

/**
 * The decoded contents of a captured email: every header plus the transfer-decoded body parts.
 * {@code textBody} and {@code htmlBody} are null when the message has no such part.
 */
public record EmailContent(
        String id,
        List<EmailHeader> headers,
        String textBody,
        String htmlBody,
        List<EmailAttachment> attachments,
        boolean truncated
) {}
