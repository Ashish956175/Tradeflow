package com.tradeflow.api.procurement;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.product.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "procurement_requests")
public class ProcurementRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_organization_id", nullable = false)
    private Organization buyerOrganization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(name = "target_unit_price", precision = 12, scale = 2)
    private BigDecimal targetUnitPrice;

    @Column(name = "delivery_address", nullable = false, length = 500)
    private String deliveryAddress;

    @Column(name = "required_delivery_date")
    private LocalDate requiredDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProcurementStatus status = ProcurementStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProcurementRequest() {
        // Required by JPA
    }

    public ProcurementRequest(
            Organization buyerOrganization,
            Product product,
            Integer requestedQuantity,
            BigDecimal targetUnitPrice,
            String deliveryAddress,
            LocalDate requiredDeliveryDate
    ) {
        this.buyerOrganization = buyerOrganization;
        this.product = product;
        this.requestedQuantity = requestedQuantity;
        this.targetUnitPrice = targetUnitPrice;
        this.deliveryAddress = deliveryAddress;
        this.requiredDeliveryDate = requiredDeliveryDate;
        this.status = ProcurementStatus.OPEN;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();

        if (this.status == null) {
            this.status = ProcurementStatus.OPEN;
        }
    }

    public Long getId() {
        return id;
    }

    public Organization getBuyerOrganization() {
        return buyerOrganization;
    }

    public Product getProduct() {
        return product;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public BigDecimal getTargetUnitPrice() {
        return targetUnitPrice;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public LocalDate getRequiredDeliveryDate() {
        return requiredDeliveryDate;
    }

    public ProcurementStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}