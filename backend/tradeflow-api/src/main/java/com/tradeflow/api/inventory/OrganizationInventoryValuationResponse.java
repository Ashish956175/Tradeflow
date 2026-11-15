package com.tradeflow.api.inventory;

import java.math.BigDecimal;

public record OrganizationInventoryValuationResponse(
        Long totalProducts,
        Long totalInventoryUnits,
        BigDecimal totalInventoryValue,
        BigDecimal averageUnitValue
) {
}
