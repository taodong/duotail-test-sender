package com.duotail.utils.email.sender;

import java.util.Map;

/**
 * Built-in mapping of RFC 3463 status codes to human-readable SMTP diagnostic strings, used to
 * populate the DSN {@code Diagnostic-Code} field when a caller does not supply {@code diagnosticText}.
 */
public final class BounceDiagnostics {

    private static final Map<String, String> DEFAULTS = Map.of(
            "5.1.1", "smtp; 550 5.1.1 User unknown; the email account does not exist",
            "5.2.2", "smtp; 552 5.2.2 Mailbox full",
            "5.7.1", "smtp; 550 5.7.1 Message rejected; delivery not authorized",
            "4.2.2", "smtp; 452 4.2.2 Mailbox over quota; try again later",
            "4.4.1", "smtp; 421 4.4.1 Connection timed out; will retry"
    );

    private BounceDiagnostics() {
    }

    /**
     * Resolves the default diagnostic text for a status code. Falls back to a generic message keyed on
     * the bounce type when the code is not in the built-in map.
     */
    public static String forStatus(String statusCode, BounceType bounceType) {
        String diagnostic = DEFAULTS.get(statusCode);
        if (diagnostic != null) {
            return diagnostic;
        }
        return bounceType == BounceType.HARD
                ? "smtp; 550 5.0.0 Permanent failure; message could not be delivered"
                : "smtp; 451 4.0.0 Temporary failure; message delivery delayed";
    }
}
