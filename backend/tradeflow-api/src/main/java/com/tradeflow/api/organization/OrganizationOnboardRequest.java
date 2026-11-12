package com.tradeflow.api.organization;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record OrganizationOnboardRequest(

        @NotBlank(message = "Organization name is required")
        @Size(max = 150, message = "Organization name cannot exceed 150 characters")
        String name,

        @NotBlank(message = "Business email is required")
        @Email(message = "Enter a valid business email")
        @Size(max = 255, message = "Business email cannot exceed 255 characters")
        String businessEmail,

        @Size(max = 20, message = "Phone number cannot exceed 20 characters")
        String phone,

        @Size(max = 500, message = "Address cannot exceed 500 characters")
        String address,

        @NotEmpty(message = "Select at least one organization capability")
        Set<OrganizationCapability> capabilities

) {}
