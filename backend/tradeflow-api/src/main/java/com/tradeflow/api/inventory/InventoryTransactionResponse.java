package com.tradeflow.api.inventory;

import java.time.LocalDateTime;

public record InventoryTransactionResponse(
        Long id,
        Long productId,
        String productName,
        String productSku,
        Long purchaseOrderId,
        Long performedById,
        String performedByEmail,
        String transactionType,
        Integer quantity,
        Integer previousQuantity,
        Integer newQuantity,
        String reason,
        LocalDateTime createdAt
) {

    public static InventoryTransactionResponse from(InventoryTransaction transaction) {
        return new InventoryTransactionResponse(
                transaction.getId(),
                transaction.getProduct().getId(),
                transaction.getProduct().getName(),
                transaction.getProduct().getSku(),
                transaction.getPurchaseOrder() != null
                        ? transaction.getPurchaseOrder().getId()
                        : null,
                transaction.getPerformedBy().getId(),
                transaction.getPerformedBy().getEmail(),
                transaction.getTransactionType().name(),
                transaction.getQuantity(),
                transaction.getPreviousQuantity(),
                transaction.getNewQuantity(),
                transaction.getReason(),
                transaction.getCreatedAt()
        );
    }
}