package com.tradeflow.api.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventorySnapshotRepository
        extends JpaRepository<InventorySnapshot, Long> {

    Optional<InventorySnapshot>
    findByOrganization_IdAndProduct_IdAndSnapshotDate(
            Long organizationId,
            Long productId,
            LocalDate snapshotDate
    );

    List<InventorySnapshot> findAllBySnapshotDate(LocalDate snapshotDate);

    List<InventorySnapshot>
    findByOrganization_IdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long organizationId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<InventorySnapshot>
    findByOrganization_IdAndProduct_IdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long organizationId,
            Long productId,
            LocalDate startDate,
            LocalDate endDate
    );
}
