package com.duotail.utils.email.mailhog.dto;

/**
 * A single header line as it appeared on the wire. Headers are carried as an ordered list
 * rather than a map because {@code Received} and {@code DKIM-Signature} legitimately repeat.
 */
public record EmailHeader(String name, String value) {}
