package com.tradeflow.api.product;


public interface InventorySummaryProjection {

    Long getTotalProducts();

    Long getTotalInventoryUnits();

    Long getLowStockProducts();

    Long getOutOfStockProducts();

    Long getHealthyStockProducts();
}
