package com.tradeflow.api.inventory;

public record OrganizationInventoryMovementAnalyticsResponse(
        Long totalStockIn,
        Long totalGoodsReceived,
        Long totalStockOut,
        Long totalShipped,
        Long adjustmentIncrease,
        Long adjustmentDecrease,
        Long netInventoryMovement,
        Long totalTransactions
) {
}
