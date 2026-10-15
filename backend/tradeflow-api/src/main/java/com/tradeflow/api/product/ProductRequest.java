package com.tradeflow.api.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(

    @NotBlank(message = "Product name is required")
    @Size(max = 150)
    String name,

    @Size(max = 1000)
    String description,

    @NotBlank(message = "SKU is required")
    @Size(max = 50)
    String sku,

    @NotNull(message = "Unit price is required")
    @DecimalMin(
        value = "0.01",
        message = "Unit price must be greater than zero"
    )
    BigDecimal unitPrice,

    @NotNull(message = "Available quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    Integer availableQuantity,

    @NotBlank(message = "Unit is required")
    @Size(max = 30)
    String unit

) {
}