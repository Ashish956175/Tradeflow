package com.tradeflow.api;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);

        when(request.getRequestURI()).thenReturn("/api/shipments");
    }

    @Test
    void handleDataIntegrityViolation_shouldReturnConflict() {

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Duplicate entry for unique constraint"
                );

        ResponseEntity<ApiError> response =
                exceptionHandler.handleDataIntegrityViolation(
                        exception,
                        request
                );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        ApiError body = response.getBody();

        assertNotNull(body);
        assertEquals(409, body.status());
        assertEquals("Conflict", body.error());
        assertEquals(
                "The request conflicts with existing data.",
                body.message()
        );
        assertEquals("/api/shipments", body.path());
        assertEquals(Map.of(), body.validationErrors());
    }

    @Test
    void handleDataIntegrityViolation_shouldNotExposeDatabaseDetails() {

        String databaseDetails =
                "Duplicate entry for uk_shipment_purchase_order";

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(databaseDetails);

        ResponseEntity<ApiError> response =
                exceptionHandler.handleDataIntegrityViolation(
                        exception,
                        request
                );

        assertNotNull(response.getBody());

        assertFalse(
                response.getBody().message().contains(databaseDetails)
        );

        assertFalse(
                response.getBody().message().contains(
                        "uk_shipment_purchase_order"
                )
        );
    }
}
