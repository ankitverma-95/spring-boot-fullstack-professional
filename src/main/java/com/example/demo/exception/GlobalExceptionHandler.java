package com.example.demo.exception;

import com.example.demo.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Objects;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException ex) {
        log.warn("CustomException caught: {} - {}", ex.getError(), ex.getMessage());
        ErrorResponse response = ErrorResponse.builder()
                .error(ex.getError())
                .message(ex.getMessage())
                .timestamp(ex.getTimestamp())
                .build();
        return new ResponseEntity<>(response, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldError() != null 
                ? ex.getBindingResult().getFieldError().getDefaultMessage()
                : "Validation failed";
        log.warn("Validation error: {}", message);
        ErrorResponse response = ErrorResponse.builder()
                .error("VALIDATION_FAILED")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("Database constraint violation: ", ex);
        String message = "Database constraint violation occurred.";
        String errorCode = "DATABASE_CONSTRAINT_VIOLATION";
        
        String rootMsg = ex.getRootCause() != null ? ex.getRootCause().getMessage() : "";
        if (rootMsg.contains("idx_active_clock_in") || rootMsg.contains("active_clock_in") || rootMsg.contains("attendance_logs_worker_id_key")) {
            errorCode = "DUPLICATE_CLOCK_IN";
            message = "Worker is already clocked in at a site and cannot double clock-in.";
        } else if (rootMsg.contains("workers_phone_key") || rootMsg.contains("idx_workers_phone")) {
            errorCode = "DUPLICATE_PHONE";
            message = "A worker with this phone number already exists.";
        } else if (rootMsg.contains("sites_site_name_key") || rootMsg.contains("idx_sites_name")) {
            errorCode = "DUPLICATE_SITE";
            message = "A site with this name already exists.";
        }
        
        ErrorResponse response = ErrorResponse.builder()
                .error(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception caught: ", ex);
        ErrorResponse response = ErrorResponse.builder()
                .error("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred: " + ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
