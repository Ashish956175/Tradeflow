package com.tradeflow.api.order;

import com.tradeflow.api.quotation.Quotation;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "purchase_orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_purchase_order_number",
                        columnNames = "order_number"
                ),
                @UniqueConstraint(
                        name = "uk_purchase_order_quotation",
                        columnNames = "quotation_id"
                )
        }
)
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "order_number",
            nullable = false,
            unique = true,
            length = 40
    )
    private String orderNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "quotation_id",
            nullable = false,
            unique = true
    )
    private Quotation quotation;

    @Column(name = "agreed_unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal agreedUnitPrice;

    @Column(name = "ordered_quantity", nullable = false)
    private Integer orderedQuantity;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "delivery_address", nullable = false, length = 500)
    private String deliveryAddress;

    @Column(name = "expected_delivery_date", nullable = false)
    private LocalDate expectedDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PurchaseOrder() {
        // Required by JPA
    }

    public PurchaseOrder(
            Quotation quotation,
            String deliveryAddress
    ) {
        this.quotation = quotation;
        this.agreedUnitPrice = quotation.getOfferedUnitPrice();
        this.orderedQuantity = quotation.getOfferedQuantity();
        this.totalAmount = agreedUnitPrice.multiply(
                BigDecimal.valueOf(orderedQuantity)
        );
        this.deliveryAddress = deliveryAddress;
        this.expectedDeliveryDate = quotation.getEstimatedDeliveryDate();
        this.status = OrderStatus.CREATED;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();

        if (this.orderNumber == null) {
            this.orderNumber = "TF-" +
                    UUID.randomUUID().toString()
                            .replace("-", "")
                            .substring(0, 12)
                            .toUpperCase();
        }

        if (this.status == null) {
            this.status = OrderStatus.CREATED;
        }
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Quotation getQuotation() {
        return quotation;
    }

    public BigDecimal getAgreedUnitPrice() {
        return agreedUnitPrice;
    }

    public Integer getOrderedQuantity() {
        return orderedQuantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public LocalDate getExpectedDeliveryDate() {
        return expectedDeliveryDate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void confirm() {
        if (this.status != OrderStatus.CREATED) {
            throw new IllegalStateException(
                    "Only newly created orders can be confirmed"
            );
        }

        this.status = OrderStatus.CONFIRMED;
    }

    public void ship() {
        if (this.status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Only confirmed orders can be shipped"
            );
        }

        this.status = OrderStatus.SHIPPED;
    }

    public void deliver() {
        if (this.status != OrderStatus.SHIPPED) {
            throw new IllegalStateException(
                    "Only shipped orders can be marked as delivered"
            );
        }

        this.status = OrderStatus.DELIVERED;
    }

    public void cancel() {
        if (this.status != OrderStatus.CREATED
                && this.status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Only created or confirmed orders can be cancelled"
            );
        }

        this.status = OrderStatus.CANCELLED;
    }

}