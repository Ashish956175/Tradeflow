package com.tradeflow.api.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository
        extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);

    Optional<PurchaseOrder> findByQuotation_Id(Long quotationId);

    List<PurchaseOrder> findByStatus(OrderStatus status);

    boolean existsByQuotation_Id(Long quotationId);
}