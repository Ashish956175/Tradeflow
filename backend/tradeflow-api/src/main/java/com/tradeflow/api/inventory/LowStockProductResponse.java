package com.tradeflow.api.inventory;

import com.tradeflow.api.product.Product;

public record LowStockProductResponse(
        Long productId,
        String productName,
        String sku,
        Integer availableQuantity,
        Integer lowStockThreshold,
        String unit,
        String status
) {

    public static LowStockProductResponse from(Product product) {

        String status = product.getAvailableQuantity() == 0
                ? "OUT_OF_STOCK"
                : "LOW_STOCK";

        return new LowStockProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getAvailableQuantity(),
                product.getLowStockThreshold(),
                product.getUnit(),
                status
        );
    }
}
