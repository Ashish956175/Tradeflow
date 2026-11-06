package com.tradeflow.api.inventory;

public record InventorySummaryResponse(
        Long totalProducts,
        Long totalInventoryUnits,
        Long lowStockProducts,
        Long outOfStockProducts,
        Long healthyStockProducts
) {
}
