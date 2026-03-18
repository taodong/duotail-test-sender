package com.duotail.utils.email.sender.permission;

public class PermissionException extends Exception {

    public PermissionException(String message) {
        super(message);
    }

    public PermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}

