package com.tradeflow.api.supplier;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SupplierRequest(

        @NotNull(message = "Organization ID is required")
        Long organizationId,

        @NotBlank(message = "Supplier name is required")
        @Size(max = 150, message = "Supplier name cannot exceed 150 characters")
        String name,

        @NotBlank(message = "Supplier email is required")
        @Email(message = "Enter a valid supplier email")
        @Size(max = 255, message = "Supplier email cannot exceed 255 characters")
        String email,

        @Size(max = 20, message = "Phone number cannot exceed 20 characters")
        String phone,

        @Size(max = 500, message = "Address cannot exceed 500 characters")
        String address

) {}