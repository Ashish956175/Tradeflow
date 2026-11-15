package com.tradeflow.api.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventorySnapshotExecutionAuditServiceTest {

    @Mock
    private InventorySnapshotExecutionRepository executionRepository;

    @InjectMocks
    private InventorySnapshotExecutionAuditService auditService;

    @Test
    void startExecution_shouldCreateRunningExecution() {

        LocalDate snapshotDate = LocalDate.of(2026, 11, 15);

        InventorySnapshotExecution savedExecution =
                mock(InventorySnapshotExecution.class);

        when(savedExecution.getId()).thenReturn(42L);

        when(executionRepository.saveAndFlush(any(InventorySnapshotExecution.class)))
                .thenReturn(savedExecution);

        Long executionId = auditService.startExecution(snapshotDate);

        assertEquals(42L, executionId);

        ArgumentCaptor<InventorySnapshotExecution> captor =
                ArgumentCaptor.forClass(InventorySnapshotExecution.class);

        verify(executionRepository).saveAndFlush(captor.capture());

        InventorySnapshotExecution execution = captor.getValue();

        assertEquals(snapshotDate, execution.getSnapshotDate());
        assertEquals(InventorySnapshotExecutionStatus.RUNNING,
                execution.getStatus());
        assertEquals(0, execution.getSnapshotsCreated());
    }

    @Test
    void markSuccessful_shouldUpdateExecutionStatusAndCount() {

        InventorySnapshotExecution execution =
                new InventorySnapshotExecution(
                        LocalDate.of(2026, 11, 15)
                );

        when(executionRepository.findById(42L))
                .thenReturn(Optional.of(execution));

        auditService.markSuccessful(42L, 25);

        assertEquals(
                InventorySnapshotExecutionStatus.SUCCESS,
                execution.getStatus()
        );

        assertEquals(25, execution.getSnapshotsCreated());
        assertNotNull(execution.getCompletedAt());
        assertNull(execution.getFailureDetails());

        verify(executionRepository).findById(42L);
    }

    @Test
    void markFailed_shouldUpdateExecutionWithFailureDetails() {

        InventorySnapshotExecution execution =
                new InventorySnapshotExecution(
                        LocalDate.of(2026, 11, 15)
                );

        when(executionRepository.findById(42L))
                .thenReturn(Optional.of(execution));

        String failureDetails = "Database connection failed.";

        auditService.markFailed(42L, failureDetails);

        assertEquals(
                InventorySnapshotExecutionStatus.FAILED,
                execution.getStatus()
        );

        assertEquals(failureDetails, execution.getFailureDetails());
        assertNotNull(execution.getCompletedAt());
        assertEquals(0, execution.getSnapshotsCreated());

        verify(executionRepository).findById(42L);
    }

    @Test
    void markFailed_shouldTruncateFailureDetailsTo2000Characters() {

        InventorySnapshotExecution execution =
                new InventorySnapshotExecution(
                        LocalDate.of(2026, 11, 15)
                );

        when(executionRepository.findById(42L))
                .thenReturn(Optional.of(execution));

        String failureDetails = "X".repeat(2500);

        auditService.markFailed(42L, failureDetails);

        assertEquals(
                InventorySnapshotExecutionStatus.FAILED,
                execution.getStatus()
        );

        assertEquals(2000, execution.getFailureDetails().length());
        assertEquals(
                failureDetails.substring(0, 2000),
                execution.getFailureDetails()
        );
    }

    @Test
    void markFailed_shouldUseDefaultMessageForBlankFailureDetails() {

        InventorySnapshotExecution execution =
                new InventorySnapshotExecution(
                        LocalDate.of(2026, 11, 15)
                );

        when(executionRepository.findById(42L))
                .thenReturn(Optional.of(execution));

        auditService.markFailed(42L, "   ");

        assertEquals(
                "Unknown inventory snapshot execution failure.",
                execution.getFailureDetails()
        );
    }

    @Test
    void markSuccessful_shouldThrowWhenExecutionDoesNotExist() {

        when(executionRepository.findById(42L))
                .thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> auditService.markSuccessful(42L, 10)
        );

        assertTrue(exception.getMessage().contains("42"));
    }
}
