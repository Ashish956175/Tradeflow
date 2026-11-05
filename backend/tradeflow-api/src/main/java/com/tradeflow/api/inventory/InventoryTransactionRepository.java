package com.tradeflow.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface InventoryTransactionRepository
        extends JpaRepository<InventoryTransaction, Long>,
        JpaSpecificationExecutor<InventoryTransaction> {

    List<InventoryTransaction> findByProduct_IdOrderByCreatedAtDesc(Long productId);

    List<InventoryTransaction> findByPurchaseOrder_IdOrderByCreatedAtDesc(Long purchaseOrderId);
}