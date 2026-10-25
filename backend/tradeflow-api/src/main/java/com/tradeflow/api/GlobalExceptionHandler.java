package com.tradeflow.api;

import com.tradeflow.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

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
                request.getRequestURI(),
                Map.of()
        );

        return ResponseEntity.status(status).body(error);
    }

    // Handle request-body validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        Map<String, String> validationErrors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(fieldError ->
                        validationErrors.putIfAbsent(
                                fieldError.getField(),
                                fieldError.getDefaultMessage() != null
                                        ? fieldError.getDefaultMessage()
                                        : "Invalid value"
                        )
                );

        return buildError(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request,
                validationErrors
        );
    }

    // Handle invalid path variables and request parameters
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {

        Map<String, String> validationErrors = new LinkedHashMap<>();

        exception.getConstraintViolations()
                .forEach(violation ->
                        validationErrors.put(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()
                        )
                );

        return buildError(
                HttpStatus.BAD_REQUEST,
                "Request parameters are invalid",
                request,
                validationErrors
        );
    }

    // Handle malformed JSON or a missing required request body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {

        return buildError(
                HttpStatus.BAD_REQUEST,
                "Request body is missing or malformed",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ApiError> buildError(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors) {

        ApiError error = new ApiError(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                validationErrors
        );

        return ResponseEntity.status(status).body(error);
    }
}
