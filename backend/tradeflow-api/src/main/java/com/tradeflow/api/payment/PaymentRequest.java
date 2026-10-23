package com.tradeflow.api.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(

        @NotNull(message = "Purchase order ID is required")
        @Positive(message = "Purchase order ID must be positive")
        Long purchaseOrderId,

        @NotBlank(message = "Payment method is required")
        String paymentMethod

) {
}