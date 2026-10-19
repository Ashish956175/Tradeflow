package com.tradeflow.api.relationship;

import jakarta.validation.constraints.NotNull;

public record RelationshipRequest(

        @NotNull(message = "Buyer organization ID is required")
        Long buyerOrganizationId,

        @NotNull(message = "Supplier ID is required")
        Long supplierId

) {}