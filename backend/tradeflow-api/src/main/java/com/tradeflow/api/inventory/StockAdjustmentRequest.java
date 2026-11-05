package com.tradeflow.api.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(

        @NotNull(message = "Product ID is required")
        Long productId,

        @NotNull(message = "Adjustment quantity is required")
        Integer adjustmentQuantity,

        @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        String reason

) {
}
