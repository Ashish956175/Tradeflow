package com.tradeflow.api.inventory;

import java.math.BigDecimal;

public interface OrganizationInventoryValuationProjection {

    Long getTotalProducts();

    Long getTotalInventoryUnits();

    BigDecimal getTotalInventoryValue();
}
