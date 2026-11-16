package com.tradeflow.api.quotation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    List<Quotation> findByProcurementRequest_Id(Long procurementRequestId);

    List<Quotation> findByProcurementRequest_IdAndSupplier_IdIn(
            Long procurementRequestId,
            List<Long> supplierIds
    );

    boolean existsByProcurementRequest_IdAndSupplier_Id(
            Long procurementRequestId,
            Long supplierId
    );
}