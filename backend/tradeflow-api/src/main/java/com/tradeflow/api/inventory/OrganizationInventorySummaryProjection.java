package com.tradeflow.api.inventory;

import java.math.BigDecimal;

public interface OrganizationInventorySummaryProjection {

    Long getTotalProducts();

    Long getTotalInventoryUnits();

    Long getTotalReservedUnits();

    Long getTotalAvailableUnits();

    Long getLowStockProducts();

    Long getOutOfStockProducts();

    Long getHealthyStockProducts();

    BigDecimal getTotalInventoryValue();
}
