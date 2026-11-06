package com.tradeflow.api.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;



public interface ProductRepository
        extends JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    boolean existsBySku(String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :productId")
    Optional<Product> findByIdForUpdate(@Param("productId") Long productId);

    @Modifying
    @Query("""
            UPDATE Product p
            SET p.availableQuantity = p.availableQuantity - :quantity
            WHERE p.id = :productId
              AND p.availableQuantity >= :quantity
            """)
    int reserveInventory(
            @Param("productId") Long productId,
            @Param("quantity") int quantity
    );

    @Modifying
    @Query("""
            UPDATE Product p
            SET p.availableQuantity = p.availableQuantity + :quantity
            WHERE p.id = :productId
            """)
    int releaseInventory(
            @Param("productId") Long productId,
            @Param("quantity") int quantity
    );

    @Query("""
            SELECT
                COUNT(p) AS totalProducts,
                COALESCE(SUM(p.availableQuantity), 0L) AS totalInventoryUnits,
                COUNT(CASE
                    WHEN p.availableQuantity > 0
                     AND p.availableQuantity <= p.lowStockThreshold
                    THEN p.id
                END) AS lowStockProducts,
                COUNT(CASE
                    WHEN p.availableQuantity = 0
                    THEN p.id
                END) AS outOfStockProducts,
                COUNT(CASE
                    WHEN p.availableQuantity > p.lowStockThreshold
                    THEN p.id
                END) AS healthyStockProducts
            FROM Product p
            """)
    InventorySummaryProjection getInventorySummary();

    @Query("""
            SELECT
                COUNT(p) AS totalProducts,
                COALESCE(SUM(p.availableQuantity), 0L) AS totalInventoryUnits,
                COALESCE(
                    SUM(p.unitPrice * p.availableQuantity),
                    0.00
                ) AS totalInventoryValue
            FROM Product p
            """)
    InventoryValuationProjection getInventoryValuation();
}