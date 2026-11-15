package com.tradeflow.api.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrganizationInventoryTransactionResponse(
        Long id,
        Long organizationId,
        Long productId,
        String productName,
        String productSku,
        Long purchaseOrderId,
        Long performedById,
        String transactionType,
        String stockOutPurpose,
        Integer quantity,
        Integer previousOnHandQuantity,
        Integer newOnHandQuantity,
        Integer previousReservedQuantity,
        Integer newReservedQuantity,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String reason,
        LocalDateTime createdAt
) {

    public static OrganizationInventoryTransactionResponse from(
            OrganizationInventoryTransaction transaction
    ) {
        return new OrganizationInventoryTransactionResponse(
                transaction.getId(),
                transaction.getOrganization().getId(),
                transaction.getProduct().getId(),
                transaction.getProduct().getName(),
                transaction.getProduct().getSku(),
                transaction.getPurchaseOrder() != null
                        ? transaction.getPurchaseOrder().getId()
                        : null,
                transaction.getPerformedBy() != null
                        ? transaction.getPerformedBy().getId()
                        : null,
                transaction.getTransactionType().name(),
                null,
                transaction.getQuantity(),
                transaction.getPreviousOnHand(),
                transaction.getNewOnHand(),
                transaction.getPreviousReserved(),
                transaction.getNewReserved(),
                transaction.getUnitCost(),
                transaction.getTotalCost(),
                transaction.getReason(),
                transaction.getCreatedAt()
        );
    }
}
