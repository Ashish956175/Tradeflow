package com.tradeflow.api.inventory;

import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.product.Product;
import com.tradeflow.api.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "organization_inventory_transactions",
        indexes = {
                @Index(name = "idx_org_inventory_tx_organization", columnList = "organization_id"),
                @Index(name = "idx_org_inventory_tx_product", columnList = "product_id"),
                @Index(name = "idx_org_inventory_tx_order", columnList = "purchase_order_id"),
                @Index(name = "idx_org_inventory_tx_user", columnList = "performed_by"),
                @Index(name = "idx_org_inventory_tx_created", columnList = "created_at"),
                @Index(name = "idx_org_inventory_tx_type", columnList = "transaction_type")
        }
)
public class OrganizationInventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private OrganizationInventoryTransactionType transactionType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "previous_on_hand", nullable = false)
    private Integer previousOnHand;

    @Column(name = "new_on_hand", nullable = false)
    private Integer newOnHand;

    @Column(name = "previous_reserved", nullable = false)
    private Integer previousReserved;

    @Column(name = "new_reserved", nullable = false)
    private Integer newReserved;

    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "total_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalCost;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OrganizationInventoryTransaction() {
        // Required by JPA.
    }

    public OrganizationInventoryTransaction(
            Organization organization,
            Product product,
            PurchaseOrder purchaseOrder,
            User performedBy,
            OrganizationInventoryTransactionType transactionType,
            Integer quantity,
            Integer previousOnHand,
            Integer newOnHand,
            Integer previousReserved,
            Integer newReserved,
            BigDecimal unitCost,
            BigDecimal totalCost,
            String reason
    ) {
        this.organization = organization;
        this.product = product;
        this.purchaseOrder = purchaseOrder;
        this.performedBy = performedBy;
        this.transactionType = transactionType;
        this.quantity = quantity;
        this.previousOnHand = previousOnHand;
        this.newOnHand = newOnHand;
        this.previousReserved = previousReserved;
        this.newReserved = newReserved;
        this.unitCost = unitCost;
        this.totalCost = totalCost;
        this.reason = reason;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
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

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    public OrganizationInventoryTransactionType getTransactionType() {
        return transactionType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Integer getPreviousOnHand() {
        return previousOnHand;
    }

    public Integer getNewOnHand() {
        return newOnHand;
    }

    public Integer getPreviousReserved() {
        return previousReserved;
    }

    public Integer getNewReserved() {
        return newReserved;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
