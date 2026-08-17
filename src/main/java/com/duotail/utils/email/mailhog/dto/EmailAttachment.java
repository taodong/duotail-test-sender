package com.duotail.utils.email.mailhog.dto;

/**
 * Metadata for an attached part. Attachment content is deliberately never inlined —
 * only enough detail to know what is attached.
 */
public record EmailAttachment(String filename, String contentType, int size) {}
