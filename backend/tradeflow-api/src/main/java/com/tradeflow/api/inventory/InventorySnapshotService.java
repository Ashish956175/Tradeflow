package com.tradeflow.api.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class InventorySnapshotService {

    private static final int COST_SCALE = 4;

    private final OrganizationInventoryRepository inventoryRepository;
    private final InventorySnapshotRepository snapshotRepository;

    public InventorySnapshotService(
            OrganizationInventoryRepository inventoryRepository,
            InventorySnapshotRepository snapshotRepository
    ) {
        this.inventoryRepository = inventoryRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional
    public int createDailySnapshots(LocalDate snapshotDate) {

        List<InventorySnapshot> existingSnapshots =
                snapshotRepository.findAllBySnapshotDate(snapshotDate);

        Set<String> existingSnapshotKeys = new HashSet<>();

        for (InventorySnapshot snapshot : existingSnapshots) {
            existingSnapshotKeys.add(
                    createSnapshotKey(
                            snapshot.getOrganization().getId(),
                            snapshot.getProduct().getId()
                    )
            );
        }

        List<OrganizationInventory> inventories =
                inventoryRepository.findAll();

        List<InventorySnapshot> snapshotsToSave = new ArrayList<>();

        for (OrganizationInventory inventory : inventories) {

            Long organizationId = inventory.getOrganization().getId();
            Long productId = inventory.getProduct().getId();

            String snapshotKey =
                    createSnapshotKey(organizationId, productId);

            if (!existingSnapshotKeys.add(snapshotKey)) {
                continue;
            }

            BigDecimal inventoryValue =
                    inventory.getAverageUnitCost()
                            .multiply(
                                    BigDecimal.valueOf(
                                            inventory.getOnHandQuantity()
                                    )
                            )
                            .setScale(COST_SCALE, RoundingMode.HALF_UP);

            InventorySnapshot snapshot = new InventorySnapshot(
                    inventory.getOrganization(),
                    inventory.getProduct(),
                    snapshotDate,
                    inventory.getOnHandQuantity(),
                    inventory.getReservedQuantity(),
                    inventory.getAverageUnitCost(),
                    inventoryValue
            );

            snapshotsToSave.add(snapshot);
        }

        if (!snapshotsToSave.isEmpty()) {
            snapshotRepository.saveAll(snapshotsToSave);
        }

        return snapshotsToSave.size();
    }

    private String createSnapshotKey(
            Long organizationId,
            Long productId
    ) {
        return organizationId + ":" + productId;
    }
}
