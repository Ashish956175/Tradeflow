package com.tradeflow.api.shipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Optional<Shipment> findByPurchaseOrder_Id(Long purchaseOrderId);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    List<Shipment> findByStatus(ShipmentStatus status);

    boolean existsByPurchaseOrder_Id(Long purchaseOrderId);

    boolean existsByTrackingNumber(String trackingNumber);
}