package com.tradeflow.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventorySnapshotExecutionRepository
        extends JpaRepository<InventorySnapshotExecution, Long> {

    List<InventorySnapshotExecution>
    findBySnapshotDateOrderByStartedAtDesc(
            java.time.LocalDate snapshotDate
    );
}
