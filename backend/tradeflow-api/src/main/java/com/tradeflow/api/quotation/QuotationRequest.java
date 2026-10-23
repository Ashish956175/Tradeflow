package com.tradeflow.api.quotation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record QuotationRequest(

        @NotNull(message = "Procurement request ID is required")
        @Positive(message = "Procurement request ID must be positive")
        Long procurementRequestId,

        @NotNull(message = "Offered unit price is required")
        @DecimalMin(
                value = "0.01",
                message = "Offered unit price must be greater than zero"
        )
        BigDecimal offeredUnitPrice,

        @NotNull(message = "Offered quantity is required")
        @Min(
                value = 1,
                message = "Offered quantity must be at least 1"
        )
        Integer offeredQuantity,

        @NotNull(message = "Estimated delivery date is required")
        @FutureOrPresent(
                message = "Estimated delivery date cannot be in the past"
        )
        LocalDate estimatedDeliveryDate

) {
}