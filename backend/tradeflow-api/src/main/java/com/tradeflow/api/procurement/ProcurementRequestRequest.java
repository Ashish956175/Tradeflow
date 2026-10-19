package com.tradeflow.api.procurement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProcurementRequestRequest(

        @NotNull(message = "Buyer organization ID is required")
        @Positive(message = "Buyer organization ID must be positive")
        Long buyerOrganizationId,

        @NotNull(message = "Product ID is required")
        @Positive(message = "Product ID must be positive")
        Long productId,

        @NotNull(message = "Requested quantity is required")
        @Min(value = 1, message = "Requested quantity must be at least 1")
        Integer requestedQuantity,

        @DecimalMin(
                value = "0.01",
                message = "Target unit price must be greater than zero"
        )
        BigDecimal targetUnitPrice,

        @NotBlank(message = "Delivery address is required")
        String deliveryAddress,

        @FutureOrPresent(message = "Required delivery date cannot be in the past")
        LocalDate requiredDeliveryDate

) {
}