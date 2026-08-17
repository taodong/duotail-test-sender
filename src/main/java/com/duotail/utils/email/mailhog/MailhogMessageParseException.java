package com.duotail.utils.email.mailhog;

public class MailhogMessageParseException extends RuntimeException {
    public MailhogMessageParseException(String id, Throwable cause) {
        super("Failed to parse MailHog message: " + id, cause);
    }
}
