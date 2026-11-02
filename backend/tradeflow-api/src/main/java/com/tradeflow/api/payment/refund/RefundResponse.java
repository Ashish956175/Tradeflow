package com.tradeflow.api.payment.refund;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(
        Long id,
        Long paymentId,
        BigDecimal amount,
        String reason,
        RefundStatus status,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPayment().getId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getFailureReason(),
                refund.getCreatedAt(),
                refund.getUpdatedAt()
        );
    }
}