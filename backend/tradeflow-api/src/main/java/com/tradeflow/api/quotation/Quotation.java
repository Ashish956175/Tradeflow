package com.tradeflow.api.quotation;

import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.supplier.Supplier;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "quotations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_quotation_request_supplier",
                        columnNames = {
                                "procurement_request_id",
                                "supplier_id"
                        }
                )
        }
)
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_request_id", nullable = false)
    private ProcurementRequest procurementRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "offered_unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal offeredUnitPrice;

    @Column(name = "offered_quantity", nullable = false)
    private Integer offeredQuantity;

    @Column(name = "estimated_delivery_date", nullable = false)
    private LocalDate estimatedDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuotationStatus status = QuotationStatus.SUBMITTED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Quotation() {
        // Required by JPA
    }

    public Quotation(
            ProcurementRequest procurementRequest,
            Supplier supplier,
            BigDecimal offeredUnitPrice,
            Integer offeredQuantity,
            LocalDate estimatedDeliveryDate
    ) {
        this.procurementRequest = procurementRequest;
        this.supplier = supplier;
        this.offeredUnitPrice = offeredUnitPrice;
        this.offeredQuantity = offeredQuantity;
        this.estimatedDeliveryDate = estimatedDeliveryDate;
        this.status = QuotationStatus.SUBMITTED;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();

        if (this.status == null) {
            this.status = QuotationStatus.SUBMITTED;
        }
    }

    public Long getId() {
        return id;
    }

    public ProcurementRequest getProcurementRequest() {
        return procurementRequest;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public BigDecimal getOfferedUnitPrice() {
        return offeredUnitPrice;
    }

    public Integer getOfferedQuantity() {
        return offeredQuantity;
    }

    public LocalDate getEstimatedDeliveryDate() {
        return estimatedDeliveryDate;
    }

    public QuotationStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void accept() {
        this.status = QuotationStatus.ACCEPTED;
    }

    public void reject() {
        this.status = QuotationStatus.REJECTED;
    }

}