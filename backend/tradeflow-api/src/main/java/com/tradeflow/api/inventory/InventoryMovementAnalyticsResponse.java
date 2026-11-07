package com.tradeflow.api.inventory;

public record InventoryMovementAnalyticsResponse(
        Long totalStockIn,
        Long totalStockOut,
        Long adjustmentIncrease,
        Long adjustmentDecrease,
        Long netInventoryMovement,
        Long totalTransactions
) {
}
