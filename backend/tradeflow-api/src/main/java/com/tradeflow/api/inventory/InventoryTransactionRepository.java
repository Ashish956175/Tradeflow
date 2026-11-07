package com.tradeflow.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryTransactionRepository
        extends JpaRepository<InventoryTransaction, Long>,
        JpaSpecificationExecutor<InventoryTransaction> {

    List<InventoryTransaction> findByProduct_IdOrderByCreatedAtDesc(Long productId);

    List<InventoryTransaction> findByPurchaseOrder_IdOrderByCreatedAtDesc(Long purchaseOrderId);

    @Query("""
            SELECT
                COALESCE(SUM(
                    CASE
                        WHEN t.transactionType = com.tradeflow.api.inventory.InventoryTransactionType.STOCK_IN
                        THEN t.quantity
                        ELSE 0
                    END
                ), 0) AS totalStockIn,

                COALESCE(SUM(
                    CASE
                        WHEN t.transactionType IN (
                            com.tradeflow.api.inventory.InventoryTransactionType.STOCK_OUT,
                            com.tradeflow.api.inventory.InventoryTransactionType.SHIPPED
                        )
                        THEN t.quantity
                        ELSE 0
                    END
                ), 0) AS totalStockOut,

                COALESCE(SUM(
                    CASE
                        WHEN t.transactionType = com.tradeflow.api.inventory.InventoryTransactionType.ADJUSTMENT
                             AND t.newQuantity > t.previousQuantity
                        THEN (t.newQuantity - t.previousQuantity)
                        ELSE 0
                    END
                ), 0) AS adjustmentIncrease,

                COALESCE(SUM(
                    CASE
                        WHEN t.transactionType = com.tradeflow.api.inventory.InventoryTransactionType.ADJUSTMENT
                             AND t.newQuantity < t.previousQuantity
                        THEN (t.previousQuantity - t.newQuantity)
                        ELSE 0
                    END
                ), 0) AS adjustmentDecrease,

                COUNT(
                    CASE
                        WHEN t.transactionType IN (
                            com.tradeflow.api.inventory.InventoryTransactionType.STOCK_IN,
                            com.tradeflow.api.inventory.InventoryTransactionType.STOCK_OUT,
                            com.tradeflow.api.inventory.InventoryTransactionType.SHIPPED,
                            com.tradeflow.api.inventory.InventoryTransactionType.ADJUSTMENT
                        )
                        THEN t.id
                        ELSE NULL
                    END
                ) AS totalTransactions

            FROM InventoryTransaction t
            WHERE (:startDate IS NULL OR t.createdAt >= :startDate)
              AND (:endDate IS NULL OR t.createdAt <= :endDate)
            """)
    InventoryMovementAnalyticsProjection getInventoryMovementAnalytics(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
