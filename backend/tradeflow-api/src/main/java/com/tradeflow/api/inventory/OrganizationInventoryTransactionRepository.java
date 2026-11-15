package com.tradeflow.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrganizationInventoryTransactionRepository
        extends JpaRepository<OrganizationInventoryTransaction, Long>,
        JpaSpecificationExecutor<OrganizationInventoryTransaction> {

    List<OrganizationInventoryTransaction>
    findByOrganization_IdOrderByCreatedAtDesc(Long organizationId);

    List<OrganizationInventoryTransaction>
    findByOrganization_IdAndProduct_IdOrderByCreatedAtDesc(
            Long organizationId,
            Long productId
    );

    List<OrganizationInventoryTransaction>
    findByOrganization_IdAndPurchaseOrder_IdOrderByCreatedAtDesc(
            Long organizationId,
            Long purchaseOrderId
    );

    List<OrganizationInventoryTransaction>
    findByOrganization_IdAndTransactionTypeOrderByCreatedAtDesc(
            Long organizationId,
            OrganizationInventoryTransactionType transactionType
    );

    @Query("""
            SELECT
                COALESCE(SUM(
                    CASE
                        WHEN t.transactionType = com.tradeflow.api.inventory.OrganizationInventoryTransactionType.STOCK_IN
                        THEN t.quantity
                        ELSE 0
                    END
                ), 0) AS totalStockIn,

                COALESCE(SUM(
                    CASE
                        WHEN t.transactionType = com.tradeflow.api.inventory.OrganizationInventoryTransactionType.GRN_ACCEPTED
                        THEN t.quantity
                        ELSE 0
                    END
                ), 0) AS totalGoodsReceived,

                COALESCE(SUM(
                    CASE
                        WHEN t.transactionType = com.tradeflow.api.inventory.OrganizationInventoryTransactionType.STOCK_OUT
                        THEN t.quantity
                        ELSE 0
                    END
                ), 0) AS totalStockOut,

                COALESCE(SUM(
                    CASE
                        WHEN t.transactionType = com.tradeflow.api.inventory.OrganizationInventoryTransactionType.SHIPPED
                        THEN t.quantity
                        ELSE 0
                    END
                ), 0) AS totalShipped,

                COALESCE(SUM(
                    CASE
                        WHEN t.transactionType = com.tradeflow.api.inventory.OrganizationInventoryTransactionType.ADJUSTMENT
                             AND t.newOnHand > t.previousOnHand
                        THEN (t.newOnHand - t.previousOnHand)
                        ELSE 0
                    END
                ), 0) AS adjustmentIncrease,

                COALESCE(SUM(
                    CASE
                        WHEN t.transactionType = com.tradeflow.api.inventory.OrganizationInventoryTransactionType.ADJUSTMENT
                             AND t.newOnHand < t.previousOnHand
                        THEN (t.previousOnHand - t.newOnHand)
                        ELSE 0
                    END
                ), 0) AS adjustmentDecrease,

                COUNT(
                    CASE
                        WHEN t.transactionType IN (
                            com.tradeflow.api.inventory.OrganizationInventoryTransactionType.STOCK_IN,
                            com.tradeflow.api.inventory.OrganizationInventoryTransactionType.GRN_ACCEPTED,
                            com.tradeflow.api.inventory.OrganizationInventoryTransactionType.STOCK_OUT,
                            com.tradeflow.api.inventory.OrganizationInventoryTransactionType.SHIPPED,
                            com.tradeflow.api.inventory.OrganizationInventoryTransactionType.ADJUSTMENT
                        )
                        THEN t.id
                        ELSE NULL
                    END
                ) AS totalTransactions

            FROM OrganizationInventoryTransaction t
            WHERE t.organization.id = :organizationId
              AND (:startDate IS NULL OR t.createdAt >= :startDate)
              AND (:endDate IS NULL OR t.createdAt <= :endDate)
            """)
    OrganizationInventoryMovementAnalyticsProjection getOrganizationInventoryMovementAnalytics(
            @Param("organizationId") Long organizationId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}