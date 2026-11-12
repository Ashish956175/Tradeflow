package com.tradeflow.api.membership;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "organization_memberships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_membership_user_organization",
                        columnNames = {"user_id", "organization_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_membership_organization_status",
                        columnList = "organization_id, status"
                ),
                @Index(
                        name = "idx_membership_user_status",
                        columnList = "user_id, status"
                )
        }
)
public class OrganizationMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_role", nullable = false, length = 20)
    private MembershipRole membershipRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.INVITED;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    protected OrganizationMembership() {
        // Required by JPA.
    }

    public OrganizationMembership(
            User user,
            Organization organization,
            MembershipRole membershipRole,
            MembershipStatus status,
            LocalDateTime joinedAt
    ) {
        this.user = user;
        this.organization = organization;
        this.membershipRole = membershipRole;
        this.status = status;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Organization getOrganization() {
        return organization;
    }

    public MembershipRole getMembershipRole() {
        return membershipRole;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
