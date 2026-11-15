package com.tradeflow.api.inventory;

public interface OrganizationInventoryMovementAnalyticsProjection {

    Long getTotalStockIn();

    Long getTotalGoodsReceived();

    Long getTotalStockOut();

    Long getTotalShipped();

    Long getAdjustmentIncrease();

    Long getAdjustmentDecrease();

    Long getTotalTransactions();
}
