package com.tradeflow.api.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizationCapabilityAuditRepository
        extends JpaRepository<OrganizationCapabilityAudit, Long> {

    List<OrganizationCapabilityAudit>
    findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
