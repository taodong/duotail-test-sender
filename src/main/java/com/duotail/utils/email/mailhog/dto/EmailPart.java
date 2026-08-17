package com.duotail.utils.email.mailhog.dto;

/**
 * A leaf part that is neither a body nor an attachment — most commonly the
 * {@code message/delivery-status} and {@code message/rfc822-headers} parts of a DSN, whose
 * contents (Status, Action, Diagnostic-Code) are the whole point of inspecting a bounce.
 */
public record EmailPart(String contentType, String content) {}
