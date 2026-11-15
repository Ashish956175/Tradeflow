package com.tradeflow.api.inventory;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "inventory_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inventory_snapshot",
                        columnNames = {
                                "organization_id",
                                "product_id",
                                "snapshot_date"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_inventory_snapshot_date",
                        columnList = "snapshot_date"
                ),
                @Index(
                        name = "idx_inventory_snapshot_organization",
                        columnList = "organization_id"
                )
        }
)
public class InventorySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "on_hand_quantity", nullable = false)
    private int onHandQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "average_unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal averageUnitCost;

    @Column(name = "inventory_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal inventoryValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected InventorySnapshot() {
    }

    public InventorySnapshot(
            Organization organization,
            Product product,
            LocalDate snapshotDate,
            int onHandQuantity,
            int reservedQuantity,
            BigDecimal averageUnitCost,
            BigDecimal inventoryValue
    ) {
        this.organization = organization;
        this.product = product;
        this.snapshotDate = snapshotDate;
        this.onHandQuantity = onHandQuantity;
        this.reservedQuantity = reservedQuantity;
        this.averageUnitCost = averageUnitCost;
        this.inventoryValue = inventoryValue;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Product getProduct() {
        return product;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public int getOnHandQuantity() {
        return onHandQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public BigDecimal getAverageUnitCost() {
        return averageUnitCost;
    }

    public BigDecimal getInventoryValue() {
        return inventoryValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}