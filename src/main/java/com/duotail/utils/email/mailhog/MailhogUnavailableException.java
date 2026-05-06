package com.duotail.utils.email.mailhog;

public class MailhogUnavailableException extends RuntimeException {

    public MailhogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
