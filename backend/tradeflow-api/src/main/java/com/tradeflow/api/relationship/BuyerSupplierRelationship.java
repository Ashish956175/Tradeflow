package com.tradeflow.api.relationship;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.supplier.Supplier;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "buyer_supplier_relationships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_buyer_supplier",
                        columnNames = {"buyer_organization_id", "supplier_id"}
                )
        }
)
public class BuyerSupplierRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_organization_id", nullable = false)
    private Organization buyerOrganization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RelationshipStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public BuyerSupplierRelationship() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        if (status == null) {
            status = RelationshipStatus.PENDING;
        }
    }

    public Long getId() {
        return id;
    }

    public Organization getBuyerOrganization() {
        return buyerOrganization;
    }

    public void setBuyerOrganization(Organization buyerOrganization) {
        this.buyerOrganization = buyerOrganization;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public RelationshipStatus getStatus() {
        return status;
    }

    public void setStatus(RelationshipStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}