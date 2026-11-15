package com.tradeflow.api.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizationInventoryRepository
        extends JpaRepository<OrganizationInventory, Long> {

    Optional<OrganizationInventory> findByOrganization_IdAndProduct_Id(
            Long organizationId,
            Long productId
    );

    boolean existsByOrganization_IdAndProduct_Id(
            Long organizationId,
            Long productId
    );

    List<OrganizationInventory> findAllByOrganization_Id(
            Long organizationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT inventory
            FROM OrganizationInventory inventory
            WHERE inventory.organization.id = :organizationId
              AND inventory.product.id = :productId
            """)
    Optional<OrganizationInventory> findByOrganizationAndProductForUpdate(
            @Param("organizationId") Long organizationId,
            @Param("productId") Long productId
    );

    @Query("""
            SELECT
                COUNT(inventory.id) AS totalProducts,
                COALESCE(SUM(inventory.onHandQuantity), 0L)
                    AS totalInventoryUnits,
                COALESCE(SUM(inventory.reservedQuantity), 0L)
                    AS totalReservedUnits,
                COALESCE(SUM(
                    inventory.onHandQuantity - inventory.reservedQuantity
                ), 0L) AS totalAvailableUnits,

                COUNT(CASE
                    WHEN inventory.onHandQuantity - inventory.reservedQuantity > 0
                     AND inventory.onHandQuantity - inventory.reservedQuantity
                         <= inventory.product.lowStockThreshold
                    THEN inventory.id
                END) AS lowStockProducts,

                COUNT(CASE
                    WHEN inventory.onHandQuantity - inventory.reservedQuantity = 0
                    THEN inventory.id
                END) AS outOfStockProducts,

                COUNT(CASE
                    WHEN inventory.onHandQuantity - inventory.reservedQuantity
                         > inventory.product.lowStockThreshold
                    THEN inventory.id
                END) AS healthyStockProducts,

                COALESCE(SUM(
                    inventory.averageUnitCost * inventory.onHandQuantity
                ), 0.0000) AS totalInventoryValue

            FROM OrganizationInventory inventory
            WHERE inventory.organization.id = :organizationId
            """)
    OrganizationInventorySummaryProjection getOrganizationInventorySummary(
            @Param("organizationId") Long organizationId
    );

    @Query("""
            SELECT
                COUNT(inventory.id) AS totalProducts,
                COALESCE(SUM(inventory.onHandQuantity), 0L)
                    AS totalInventoryUnits,
                COALESCE(SUM(
                    inventory.averageUnitCost * inventory.onHandQuantity
                ), 0.0000) AS totalInventoryValue

            FROM OrganizationInventory inventory
            WHERE inventory.organization.id = :organizationId
            """)
    OrganizationInventoryValuationProjection getOrganizationInventoryValuation(
            @Param("organizationId") Long organizationId
    );
}
