package com.tradeflow.api.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPurchaseOrder_Id(Long purchaseOrderId);

    List<Payment> findByStatus(PaymentStatus status);

    boolean existsByPurchaseOrder_Id(Long purchaseOrderId);
}