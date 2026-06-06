package com.example.demo.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends CustomException {
    public BadRequestException(String error, String message) {
        super(error, message, HttpStatus.BAD_REQUEST);
    }
}
