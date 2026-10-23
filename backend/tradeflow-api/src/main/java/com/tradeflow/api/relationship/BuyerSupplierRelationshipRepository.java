package com.tradeflow.api.relationship;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuyerSupplierRelationshipRepository
        extends JpaRepository<BuyerSupplierRelationship, Long> {

    boolean existsByBuyerOrganization_IdAndSupplier_Id(
            Long buyerOrganizationId,
            Long supplierId
    );

    List<BuyerSupplierRelationship>
    findAllByBuyerOrganization_Id(Long buyerOrganizationId);

    List<BuyerSupplierRelationship>
    findAllBySupplier_Id(Long supplierId);

    Optional<BuyerSupplierRelationship>
    findByBuyerOrganization_IdAndSupplier_Id(
            Long buyerOrganizationId,
            Long supplierId
    );
}