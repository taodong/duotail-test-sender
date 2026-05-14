package com.duotail.utils.email.mailhog;

public class MailhogMessageNotFoundException extends RuntimeException {

    public MailhogMessageNotFoundException(String id) {
        super("MailHog message not found: " + id);
    }
}
