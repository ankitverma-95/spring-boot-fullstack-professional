package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends CustomException {
    public NotFoundException(String error, String message) {
        super(error, message, HttpStatus.NOT_FOUND);
    }
}
