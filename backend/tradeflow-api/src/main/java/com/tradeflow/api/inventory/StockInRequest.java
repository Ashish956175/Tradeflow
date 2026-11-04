package com.tradeflow.api.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockInRequest(

        @NotNull(message = "Product ID is required")
        @Min(value = 1, message = "Product ID must be greater than zero")
        Long productId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Stock-in quantity must be greater than zero")
        Integer quantity,

        @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        String reason

) {
}