package com.tradeflow.api.inventory;

import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.product.Product;
import com.tradeflow.api.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "inventory_transactions",
        indexes = {
                @Index(name = "idx_inventory_transaction_product", columnList = "product_id"),
                @Index(name = "idx_inventory_transaction_purchase_order", columnList = "purchase_order_id"),
                @Index(name = "idx_inventory_transaction_user", columnList = "performed_by"),
                @Index(name = "idx_inventory_transaction_created_at", columnList = "created_at")
        }
)
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", nullable = false)
    private User performedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private InventoryTransactionType transactionType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "previous_quantity", nullable = false)
    private Integer previousQuantity;

    @Column(name = "new_quantity", nullable = false)
    private Integer newQuantity;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected InventoryTransaction() {
        // Required by JPA.
    }

    public InventoryTransaction(
            Product product,
            PurchaseOrder purchaseOrder,
            User performedBy,
            InventoryTransactionType transactionType,
            Integer quantity,
            Integer previousQuantity,
            Integer newQuantity,
            String reason
    ) {
        this.product = product;
        this.purchaseOrder = purchaseOrder;
        this.performedBy = performedBy;
        this.transactionType = transactionType;
        this.quantity = quantity;
        this.previousQuantity = previousQuantity;
        this.newQuantity = newQuantity;
        this.reason = reason;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
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

    public InventoryTransactionType getTransactionType() {
        return transactionType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Integer getPreviousQuantity() {
        return previousQuantity;
    }

    public Integer getNewQuantity() {
        return newQuantity;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
