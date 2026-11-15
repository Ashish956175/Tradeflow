package com.tradeflow.api.inventory;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.product.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "organization_inventory",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_organization_inventory",
                        columnNames = {"organization_id", "product_id"}
                )
        }
)
public class OrganizationInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "on_hand_quantity", nullable = false)
    private Integer onHandQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity = 0;

    @Column(
            name = "average_unit_cost",
            nullable = false,
            precision = 14,
            scale = 4
    )
    private BigDecimal averageUnitCost = new BigDecimal("0.0000");

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public OrganizationInventory() {
    }

    public OrganizationInventory(Organization organization, Product product) {
        this.organization = organization;
        this.product = product;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getOnHandQuantity() {
        return onHandQuantity;
    }

    public void setOnHandQuantity(Integer onHandQuantity) {
        this.onHandQuantity = onHandQuantity;
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public BigDecimal getAverageUnitCost() {
        return averageUnitCost;
    }

    public void setAverageUnitCost(BigDecimal averageUnitCost) {
        this.averageUnitCost = averageUnitCost;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
