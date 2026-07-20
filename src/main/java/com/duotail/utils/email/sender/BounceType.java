package com.duotail.utils.email.sender;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Category of a simulated bounce.
 *
 * <ul>
 *   <li>{@code HARD} — permanent failure, RFC 3463 status class {@code 5.x.x}, DSN action {@code failed}.</li>
 *   <li>{@code SOFT} — transient failure, RFC 3463 status class {@code 4.x.x}, DSN action {@code delayed}.</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum BounceType {
    HARD("5.1.1", "failed", "Failure", "5."),
    SOFT("4.2.2", "delayed", "Delay", "4.");

    /** Default RFC 3463 status code used when the request does not supply one. */
    private final String defaultStatusCode;
    /** DSN {@code Action} field value. */
    private final String action;
    /** Suffix used in the bounce {@code Subject} line. */
    private final String subjectSuffix;
    /** RFC 3463 status class prefix a supplied {@code statusCode} must match. */
    private final String statusClassPrefix;
}
