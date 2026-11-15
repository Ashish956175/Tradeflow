package com.tradeflow.api.inventory;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.product.Product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventorySnapshotServiceTest {

    @Mock
    private OrganizationInventoryRepository inventoryRepository;

    @Mock
    private InventorySnapshotRepository snapshotRepository;

    @InjectMocks
    private InventorySnapshotService snapshotService;

    private OrganizationInventory createInventory(
            Long organizationId,
            Long productId,
            int onHand,
            int reserved,
            String averageUnitCost
    ) {
        Organization organization = mock(Organization.class);
        Product product = mock(Product.class);

        when(organization.getId()).thenReturn(organizationId);
        when(product.getId()).thenReturn(productId);

        OrganizationInventory inventory =
                mock(OrganizationInventory.class);

        when(inventory.getOrganization()).thenReturn(organization);
        when(inventory.getProduct()).thenReturn(product);
        when(inventory.getOnHandQuantity()).thenReturn(onHand);
        when(inventory.getReservedQuantity()).thenReturn(reserved);
        when(inventory.getAverageUnitCost())
                .thenReturn(new BigDecimal(averageUnitCost));

        return inventory;
    }

    @Test
    void createDailySnapshots_shouldCreateSnapshotsForAllInventories() {

        OrganizationInventory first = createInventory(
                1L, 10L, 100, 20, "12.5000"
        );

        OrganizationInventory second = createInventory(
                2L, 10L, 50, 10, "20.0000"
        );

        when(snapshotRepository.findAllBySnapshotDate(any(LocalDate.class)))
                .thenReturn(List.of());

        when(inventoryRepository.findAll())
                .thenReturn(List.of(first, second));

        int result = snapshotService.createDailySnapshots(LocalDate.of(2026, 11, 15));

        assertEquals(2, result);

        ArgumentCaptor<List<InventorySnapshot>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(snapshotRepository).saveAll(captor.capture());

        List<InventorySnapshot> snapshots = captor.getValue();

        assertEquals(2, snapshots.size());

        assertEquals(100, snapshots.get(0).getOnHandQuantity());
        assertEquals(20, snapshots.get(0).getReservedQuantity());
        assertEquals(new BigDecimal("12.5000"),
                snapshots.get(0).getAverageUnitCost());
        assertEquals(new BigDecimal("1250.0000"),
                snapshots.get(0).getInventoryValue());

        assertEquals(50, snapshots.get(1).getOnHandQuantity());
        assertEquals(new BigDecimal("20.0000"),
                snapshots.get(1).getAverageUnitCost());
        assertEquals(new BigDecimal("1000.0000"),
                snapshots.get(1).getInventoryValue());

        assertEquals(
                LocalDate.of(2026, 11, 15),
                snapshots.get(0).getSnapshotDate()
        );
    }

    @Test
    void createDailySnapshots_shouldSkipExistingSnapshots() {

        Organization organization = mock(Organization.class);
        Product product = mock(Product.class);
        InventorySnapshot existingSnapshot = mock(InventorySnapshot.class);

        when(organization.getId()).thenReturn(1L);
        when(product.getId()).thenReturn(10L);

        when(existingSnapshot.getOrganization()).thenReturn(organization);
        when(existingSnapshot.getProduct()).thenReturn(product);

        OrganizationInventory inventory =
                mock(OrganizationInventory.class);

        when(inventory.getOrganization()).thenReturn(organization);
        when(inventory.getProduct()).thenReturn(product);
        when(snapshotRepository.findAllBySnapshotDate(any(LocalDate.class)))
                .thenReturn(List.of(existingSnapshot));

        when(inventoryRepository.findAll())
                .thenReturn(List.of(inventory));

        int result = snapshotService.createDailySnapshots(LocalDate.of(2026, 11, 15));

        assertEquals(0, result);

        verify(snapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void createDailySnapshots_shouldIncludeZeroStockInventory() {

        OrganizationInventory inventory = createInventory(
                1L, 10L, 0, 0, "15.5000"
        );

        when(snapshotRepository.findAllBySnapshotDate(any(LocalDate.class)))
                .thenReturn(List.of());

        when(inventoryRepository.findAll())
                .thenReturn(List.of(inventory));

        int result = snapshotService.createDailySnapshots(LocalDate.of(2026, 11, 15));

        assertEquals(1, result);

        ArgumentCaptor<List<InventorySnapshot>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(snapshotRepository).saveAll(captor.capture());

        InventorySnapshot snapshot = captor.getValue().get(0);

        assertEquals(0, snapshot.getOnHandQuantity());
        assertEquals(0, snapshot.getReservedQuantity());
        assertEquals(new BigDecimal("0.0000"),
                snapshot.getInventoryValue());
        assertEquals(new BigDecimal("15.5000"),
                snapshot.getAverageUnitCost());
    }

    @Test
    void createDailySnapshots_shouldKeepOrganizationsIndependent() {

        OrganizationInventory first = createInventory(
                1L, 10L, 100, 0, "10.0000"
        );

        OrganizationInventory second = createInventory(
                2L, 10L, 200, 0, "20.0000"
        );

        when(snapshotRepository.findAllBySnapshotDate(any(LocalDate.class)))
                .thenReturn(List.of());

        when(inventoryRepository.findAll())
                .thenReturn(List.of(first, second));

        int result = snapshotService.createDailySnapshots(LocalDate.of(2026, 11, 15));

        assertEquals(2, result);

        ArgumentCaptor<List<InventorySnapshot>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(snapshotRepository).saveAll(captor.capture());

        List<InventorySnapshot> snapshots = captor.getValue();

        assertEquals(
                1L,
                snapshots.get(0).getOrganization().getId()
        );

        assertEquals(
                2L,
                snapshots.get(1).getOrganization().getId()
        );

        assertEquals(new BigDecimal("1000.0000"),
                snapshots.get(0).getInventoryValue());

        assertEquals(new BigDecimal("4000.0000"),
                snapshots.get(1).getInventoryValue());
    }

    @Test
    void createDailySnapshots_shouldNotSaveWhenInventoryIsEmpty() {

        when(snapshotRepository.findAllBySnapshotDate(any(LocalDate.class)))
                .thenReturn(List.of());

        when(inventoryRepository.findAll())
                .thenReturn(List.of());

        int result = snapshotService.createDailySnapshots(LocalDate.of(2026, 11, 15));

        assertEquals(0, result);

        verify(snapshotRepository, never()).saveAll(anyList());
    }
}
