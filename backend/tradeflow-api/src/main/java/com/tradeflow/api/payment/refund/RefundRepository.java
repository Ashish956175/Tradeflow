package com.tradeflow.api.payment.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByPayment_IdOrderByCreatedAtDesc(Long paymentId);

    List<Refund> findByStatus(RefundStatus status);

    @Query("""
        SELECT COALESCE(SUM(r.amount), 0)
        FROM Refund r
        WHERE r.payment.id = :paymentId
        AND r.status IN :statuses
        """)
    BigDecimal sumRefundAmountsByPaymentAndStatuses(
            @Param("paymentId") Long paymentId,
            @Param("statuses") List<RefundStatus> statuses
    );

    boolean existsByPayment_Id(Long paymentId);
}