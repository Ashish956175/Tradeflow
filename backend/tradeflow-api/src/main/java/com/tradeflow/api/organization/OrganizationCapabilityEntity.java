package com.tradeflow.api.organization;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "organization_capabilities",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_organization_capability",
                        columnNames = {"organization_id", "capability"}
                )
        }
)
public class OrganizationCapabilityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationCapability capability;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OrganizationCapabilityEntity() {
        // Required by JPA.
    }

    public OrganizationCapabilityEntity(
            Organization organization,
            OrganizationCapability capability
    ) {
        this.organization = organization;
        this.capability = capability;
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

    public OrganizationCapability getCapability() {
        return capability;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
