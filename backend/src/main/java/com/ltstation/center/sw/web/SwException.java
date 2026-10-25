package com.ltstation.center.sw.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Message affiché à l'opérateur (MessageBox.Show de l'original). */
public class SwException extends RuntimeException {

    private final int status;

    public SwException(String message) {
        this(message, 400);
    }

    public SwException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    /** Renvoie {message} avec le code HTTP de l'exception. */
    @RestControllerAdvice(basePackages = "com.ltstation.center.sw")
    public static class Handler {
        @ExceptionHandler(SwException.class)
        public ResponseEntity<Map<String, Object>> sw(SwException e) {
            return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage() == null ? "" : e.getMessage()));
        }

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<Map<String, Object>> other(RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message",
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }
}
