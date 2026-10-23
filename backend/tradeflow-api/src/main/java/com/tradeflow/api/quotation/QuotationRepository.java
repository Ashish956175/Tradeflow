package com.tradeflow.api.quotation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    List<Quotation> findByProcurementRequest_Id(Long procurementRequestId);

    boolean existsByProcurementRequest_IdAndSupplier_Id(
            Long procurementRequestId,
            Long supplierId
    );
}