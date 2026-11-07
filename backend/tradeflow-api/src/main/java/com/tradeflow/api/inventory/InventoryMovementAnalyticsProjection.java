package com.tradeflow.api.inventory;

public interface InventoryMovementAnalyticsProjection {

    Long getTotalStockIn();

    Long getTotalStockOut();

    Long getAdjustmentIncrease();

    Long getAdjustmentDecrease();

    Long getTotalTransactions();
}
