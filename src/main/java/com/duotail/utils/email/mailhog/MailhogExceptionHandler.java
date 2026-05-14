package com.duotail.utils.email.mailhog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class MailhogExceptionHandler {

    @SuppressWarnings("unused")
    @ExceptionHandler(MailhogUnavailableException.class)
    public ResponseEntity<Map<String, String>> handle(MailhogUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @SuppressWarnings("unused")
    @ExceptionHandler(MailhogMessageNotFoundException.class)
    public ResponseEntity<Map<String, String>> handle(MailhogMessageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }
}
