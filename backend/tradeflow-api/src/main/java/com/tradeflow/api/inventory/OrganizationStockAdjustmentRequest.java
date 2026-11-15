package com.tradeflow.api.inventory;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrganizationStockAdjustmentRequest(

        @NotNull(message = "Product ID is required")
        @Min(value = 1, message = "Product ID must be greater than zero")
        Long productId,

        @NotNull(message = "Adjustment quantity is required")
        Integer adjustmentQuantity,

        @DecimalMin(value = "0.0000", message = "Unit cost cannot be negative")
        @Digits(integer = 10, fraction = 4, message = "Unit cost can have at most 4 decimal places")
        BigDecimal unitCost,

        @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        String reason

) {

    @AssertTrue(message = "Adjustment quantity cannot be zero")
    public boolean isAdjustmentQuantityNonZero() {
        return adjustmentQuantity != null && adjustmentQuantity != 0;
    }
}
