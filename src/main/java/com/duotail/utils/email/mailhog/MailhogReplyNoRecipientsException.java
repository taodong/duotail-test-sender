package com.duotail.utils.email.mailhog;

public class MailhogReplyNoRecipientsException extends RuntimeException {

    public MailhogReplyNoRecipientsException(String id) {
        super("No recipients to reply to for MailHog message: " + id);
    }
}
