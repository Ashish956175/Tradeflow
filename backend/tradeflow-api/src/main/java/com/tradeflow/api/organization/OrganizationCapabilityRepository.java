package com.tradeflow.api.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationCapabilityRepository
        extends JpaRepository<OrganizationCapabilityEntity, Long> {

    List<OrganizationCapabilityEntity> findByOrganizationId(Long organizationId);

    boolean existsByOrganizationIdAndCapability(
            Long organizationId,
            OrganizationCapability capability
    );
}
