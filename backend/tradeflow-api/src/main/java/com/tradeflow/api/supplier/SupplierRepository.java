package com.tradeflow.api.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByOrganization_IdAndEmail(
            Long organizationId,
            String email
    );

    List<Supplier> findAllByOrganization_Id(Long organizationId);
}