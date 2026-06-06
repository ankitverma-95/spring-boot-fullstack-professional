package com.example.demo.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
public class CustomException extends RuntimeException {
    private final String error;
    private final HttpStatus status;
    private final LocalDateTime timestamp;

    public CustomException(String error, String message, HttpStatus status) {
        super(message);
        this.error = error;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }
}
