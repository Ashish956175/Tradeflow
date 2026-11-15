package com.tradeflow.api.inventory;

import java.math.BigDecimal;

public record OrganizationInventorySummaryResponse(
        Long totalProducts,
        Long totalInventoryUnits,
        Long totalReservedUnits,
        Long totalAvailableUnits,
        Long lowStockProducts,
        Long outOfStockProducts,
        Long healthyStockProducts,
        BigDecimal totalInventoryValue
) {
}
