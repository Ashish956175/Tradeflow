package com.tradeflow.api.relationship;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RelationshipRequest(

        @NotNull(message = "Supplier ID is required")
        @Positive(message = "Supplier ID must be positive")
        Long supplierId

) {}
