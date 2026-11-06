package com.tradeflow.api.inventory;

import java.math.BigDecimal;

public record InventoryValuationResponse(
        Long totalProducts,
        Long totalInventoryUnits,
        BigDecimal totalInventoryValue,
        BigDecimal averageUnitValue
) {
}
