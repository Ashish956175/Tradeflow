package com.tradeflow.api.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Create a payment for a confirmed purchase order
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.createPayment(
                request,
                jwt.getSubject()
        );
    }

    // Retrieve all payments (admin only, enforced in service)
    @GetMapping
    public List<PaymentResponse> getAllPayments(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.getAllPayments(jwt.getSubject());
    }

    // Retrieve a payment by its ID
    @GetMapping("/{id}")
    public PaymentResponse getPaymentById(
            @PathVariable
            @Positive(message = "Payment ID must be positive")
            Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.getPaymentById(
                id,
                jwt.getSubject()
        );
    }

    // Retrieve a payment by purchase order ID
    @GetMapping("/order/{purchaseOrderId}")
    public PaymentResponse getPaymentByOrderId(
            @PathVariable
            @Positive(message = "Purchase order ID must be positive")
            Long purchaseOrderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.getPaymentByOrderId(
                purchaseOrderId,
                jwt.getSubject()
        );
    }

    // Filter payments by status (admin only, enforced in service)
    @GetMapping("/status/{status}")
    public List<PaymentResponse> getPaymentsByStatus(
            @PathVariable PaymentStatus status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.getPaymentsByStatus(
                status,
                jwt.getSubject()
        );
    }

    // Simulate successful payment processing
    @PatchMapping("/{id}/complete")
    public PaymentResponse markPaymentAsCompleted(
            @PathVariable
            @Positive(message = "Payment ID must be positive")
            Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.markPaymentAsCompleted(
                id,
                jwt.getSubject()
        );
    }

    // Simulate failed payment processing
    @PatchMapping("/{id}/fail")
    public PaymentResponse markPaymentAsFailed(
            @PathVariable
            @Positive(message = "Payment ID must be positive")
            Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.markPaymentAsFailed(
                id,
                jwt.getSubject()
        );
    }
}