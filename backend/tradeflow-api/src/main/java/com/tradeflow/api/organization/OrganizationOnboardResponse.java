package com.tradeflow.api.organization;

public record OrganizationOnboardResponse(
        String message,
        Long organizationId,
        String organizationName
) {}