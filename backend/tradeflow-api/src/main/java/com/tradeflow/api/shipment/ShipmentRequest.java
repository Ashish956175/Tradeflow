package com.tradeflow.api.shipment;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ShipmentRequest(

        @NotNull(message = "Purchase order ID is required")
        @Positive(message = "Purchase order ID must be positive")
        Long purchaseOrderId,

        @NotBlank(message = "Carrier name is required")
        @Size(max = 100, message = "Carrier name cannot exceed 100 characters")
        String carrierName,

        @NotBlank(message = "Tracking number is required")
        @Size(max = 100, message = "Tracking number cannot exceed 100 characters")
        String trackingNumber,

        @NotBlank(message = "Delivery address is required")
        @Size(max = 500, message = "Delivery address cannot exceed 500 characters")
        String deliveryAddress,

        @NotNull(message = "Estimated delivery date is required")
        @FutureOrPresent(message = "Estimated delivery date cannot be in the past")
        LocalDate estimatedDeliveryDate

) {
}