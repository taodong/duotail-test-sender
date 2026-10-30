package com.duotail.utils.email.mailhog;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Every response presets {@code Content-Type: application/json}: otherwise Spring negotiates the
 * error body against the request's {@code Accept}, and a client downloading an eml with
 * {@code Accept: message/rfc822} would get a 500 instead of the JSON error.
 */
@RestControllerAdvice
public class MailhogExceptionHandler {

    @SuppressWarnings("unused")
    @ExceptionHandler(MailhogUnavailableException.class)
    public ResponseEntity<Map<String, String>> handle(MailhogUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", ex.getMessage()));
    }

    @SuppressWarnings("unused")
    @ExceptionHandler(MailhogMessageNotFoundException.class)
    public ResponseEntity<Map<String, String>> handle(MailhogMessageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", ex.getMessage()));
    }

    /**
     * The message exists but leaves nobody to reply to, which is a problem with the request, not the server.
     */
    @SuppressWarnings("unused")
    @ExceptionHandler(MailhogReplyNoRecipientsException.class)
    public ResponseEntity<Map<String, String>> handle(MailhogReplyNoRecipientsException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", ex.getMessage()));
    }

    /**
     * Distinct from the 404s above: the message was found, it just could not be parsed.
     */
    @SuppressWarnings("unused")
    @ExceptionHandler(MailhogMessageParseException.class)
    public ResponseEntity<Map<String, String>> handle(MailhogMessageParseException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", ex.getMessage()));
    }
}
