package com.tradeflow.api.shipment;

import com.tradeflow.api.order.PurchaseOrder;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "shipments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_shipment_tracking_number",
                        columnNames = "tracking_number"
                ),
                @UniqueConstraint(
                        name = "uk_shipment_purchase_order",
                        columnNames = "purchase_order_id"
                )
        }
)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "purchase_order_id",
            nullable = false,
            unique = true
    )
    private PurchaseOrder purchaseOrder;

    @Column(name = "tracking_number", length = 100, unique = true)
    private String trackingNumber;

    @Column(length = 100)
    private String carrierName;

    @Column(nullable = false, length = 500)
    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShipmentStatus status = ShipmentStatus.CREATED;

    private LocalDate estimatedDeliveryDate;

    private LocalDate actualDeliveryDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Shipment() {
        // Required by JPA
    }

    public Shipment(
            PurchaseOrder purchaseOrder,
            String carrierName,
            String trackingNumber,
            String deliveryAddress,
            LocalDate estimatedDeliveryDate
    ) {
        if (purchaseOrder == null) {
            throw new IllegalArgumentException(
                    "Purchase order cannot be null"
            );
        }

        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "Delivery address cannot be blank"
            );
        }

        this.purchaseOrder = purchaseOrder;
        this.carrierName = carrierName;
        this.trackingNumber = trackingNumber;
        this.deliveryAddress = deliveryAddress;
        this.estimatedDeliveryDate = estimatedDeliveryDate;
        this.status = ShipmentStatus.CREATED;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public LocalDate getEstimatedDeliveryDate() {
        return estimatedDeliveryDate;
    }

    public LocalDate getActualDeliveryDate() {
        return actualDeliveryDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }

    public void setEstimatedDeliveryDate(LocalDate estimatedDeliveryDate) {
        this.estimatedDeliveryDate = estimatedDeliveryDate;
    }

    public void setActualDeliveryDate(LocalDate actualDeliveryDate) {
        this.actualDeliveryDate = actualDeliveryDate;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }
}