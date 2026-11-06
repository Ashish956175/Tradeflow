package com.tradeflow.api.product;

import java.math.BigDecimal;

public interface InventoryValuationProjection {

    Long getTotalProducts();

    Long getTotalInventoryUnits();

    BigDecimal getTotalInventoryValue();
}
