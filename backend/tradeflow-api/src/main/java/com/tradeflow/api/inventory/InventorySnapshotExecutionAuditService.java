package com.tradeflow.api.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class InventorySnapshotExecutionAuditService {

    private static final int MAX_FAILURE_DETAILS_LENGTH = 2000;

    private final InventorySnapshotExecutionRepository executionRepository;

    public InventorySnapshotExecutionAuditService(
            InventorySnapshotExecutionRepository executionRepository
    ) {
        this.executionRepository = executionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long startExecution(LocalDate snapshotDate) {

        InventorySnapshotExecution execution =
                new InventorySnapshotExecution(snapshotDate);

        InventorySnapshotExecution savedExecution =
                executionRepository.saveAndFlush(execution);

        return savedExecution.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccessful(
            Long executionId,
            int snapshotsCreated
    ) {
        InventorySnapshotExecution execution =
                findExecution(executionId);

        execution.markSuccessful(snapshotsCreated);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            Long executionId,
            String failureDetails
    ) {
        InventorySnapshotExecution execution =
                findExecution(executionId);

        execution.markFailed(truncateFailureDetails(failureDetails));
    }

    private InventorySnapshotExecution findExecution(Long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Inventory snapshot execution not found: " + executionId
                ));
    }

    private String truncateFailureDetails(String failureDetails) {

        if (failureDetails == null || failureDetails.isBlank()) {
            return "Unknown inventory snapshot execution failure.";
        }

        if (failureDetails.length() <= MAX_FAILURE_DETAILS_LENGTH) {
            return failureDetails;
        }

        return failureDetails.substring(0, MAX_FAILURE_DETAILS_LENGTH);
    }
}
