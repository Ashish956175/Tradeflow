package com.tradeflow.api.membership;

import com.tradeflow.api.organization.OrganizationCapability;
import jakarta.persistence.*;

@Entity
@Table(
        name = "membership_capabilities",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_membership_capability",
                        columnNames = {"membership_id", "capability"}
                )
        }
)
public class MembershipCapability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private OrganizationMembership membership;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationCapability capability;

    protected MembershipCapability() {
        // Required by JPA.
    }

    public MembershipCapability(
            OrganizationMembership membership,
            OrganizationCapability capability
    ) {
        this.membership = membership;
        this.capability = capability;
    }

    public Long getId() {
        return id;
    }

    public OrganizationMembership getMembership() {
        return membership;
    }

    public OrganizationCapability getCapability() {
        return capability;
    }
}
