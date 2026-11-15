package com.tradeflow.api.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrganizationStockInRequest(

        @NotNull(message = "Product ID is required")
        @Min(value = 1, message = "Product ID must be greater than zero")
        Long productId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Stock-in quantity must be greater than zero")
        Integer quantity,

        @NotNull(message = "Unit cost is required")
        @DecimalMin(value = "0.0000", message = "Unit cost cannot be negative")
        @Digits(integer = 10, fraction = 4, message = "Unit cost can have at most 4 decimal places")
        BigDecimal unitCost,

        @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        String reason

) {
}
