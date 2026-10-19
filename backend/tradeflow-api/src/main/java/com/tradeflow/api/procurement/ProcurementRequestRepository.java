package com.tradeflow.api.procurement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcurementRequestRepository
        extends JpaRepository<ProcurementRequest, Long> {

    List<ProcurementRequest> findByBuyerOrganization_Id(Long buyerOrganizationId);
}