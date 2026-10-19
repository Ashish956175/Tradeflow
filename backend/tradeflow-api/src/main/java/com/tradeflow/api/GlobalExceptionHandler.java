package com.tradeflow.api;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handle exceptions with an explicitly defined HTTP status
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.valueOf(
            exception.getStatusCode().value()
        );

        ApiError error = new ApiError(
            status.value(),
            status.getReasonPhrase(),
            exception.getReason() != null
                ? exception.getReason()
                : "Request failed",
            request.getRequestURI()
        );

        return ResponseEntity.status(status).body(error);
    }

    // Handle request validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String message = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fieldError ->
                fieldError.getField() + " " +
                fieldError.getDefaultMessage()
            )
            .findFirst()
            .orElse("Request validation failed");

        ApiError error = new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            message,
            request.getRequestURI()
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }
}