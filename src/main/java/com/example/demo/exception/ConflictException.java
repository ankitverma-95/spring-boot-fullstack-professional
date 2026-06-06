package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends CustomException {
    public ConflictException(String error, String message) {
        super(error, message, HttpStatus.CONFLICT);
    }
}
