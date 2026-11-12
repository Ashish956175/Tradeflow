package com.tradeflow.api.membership;

import com.tradeflow.api.organization.OrganizationCapability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MembershipCapabilityRepository
        extends JpaRepository<MembershipCapability, Long> {

    List<MembershipCapability> findByMembershipId(Long membershipId);

    boolean existsByMembershipIdAndCapability(
            Long membershipId,
            OrganizationCapability capability
    );

    void deleteByMembershipId(Long membershipId);
}
