package com.tradeflow.api.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long purchaseOrderId,
        BigDecimal amount,
        PaymentStatus status,
        String paymentMethod,
        String transactionReference,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPurchaseOrder().getId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaymentMethod(),
                payment.getTransactionReference(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}