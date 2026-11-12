package com.tradeflow.api.organization;

import com.tradeflow.api.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "organization_capability_audit",
        indexes = {
                @Index(
                        name = "idx_capability_audit_organization",
                        columnList = "organization_id"
                ),
                @Index(
                        name = "idx_capability_audit_actor",
                        columnList = "changed_by_user_id"
                )
        }
)
public class OrganizationCapabilityAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationCapability capability;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationCapabilityAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedBy;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OrganizationCapabilityAudit() {
        // Required by JPA.
    }

    public OrganizationCapabilityAudit(
            Organization organization,
            OrganizationCapability capability,
            OrganizationCapabilityAction action,
            User changedBy,
            String reason
    ) {
        this.organization = organization;
        this.capability = capability;
        this.action = action;
        this.changedBy = changedBy;
        this.reason = reason;
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

    public OrganizationCapabilityAction getAction() {
        return action;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
