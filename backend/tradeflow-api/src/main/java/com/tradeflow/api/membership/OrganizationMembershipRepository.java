package com.tradeflow.api.membership;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationMembershipRepository
        extends JpaRepository<OrganizationMembership, Long> {

    Optional<OrganizationMembership> findByUserIdAndOrganizationId(
            Long userId,
            Long organizationId
    );

    List<OrganizationMembership> findByUserIdAndStatus(
            Long userId,
            MembershipStatus status
    );

    List<OrganizationMembership> findByOrganizationIdAndStatus(
            Long organizationId,
            MembershipStatus status
    );

    boolean existsByUserIdAndOrganizationId(
            Long userId,
            Long organizationId
    );
}
