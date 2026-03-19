package com.duotail.utils.email.sender;

import com.duotail.utils.email.sender.permission.PermissionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PermissionExceptionHandler {

    @ExceptionHandler(PermissionException.class)
    public ResponseEntity<Map<String, String>> handlePermissionException(PermissionException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", exception.getMessage()));
    }
}

