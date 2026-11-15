package com.tradeflow.api.inventory;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "inventory_snapshot_executions",
        indexes = {
                @Index(name = "idx_snapshot_execution_date", columnList = "snapshot_date"),
                @Index(name = "idx_snapshot_execution_status", columnList = "status"),
                @Index(name = "idx_snapshot_execution_started", columnList = "started_at")
        }
)
public class InventorySnapshotExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false, updatable = false)
    private LocalDate snapshotDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventorySnapshotExecutionStatus status;

    @Column(name = "snapshots_created", nullable = false)
    private Integer snapshotsCreated = 0;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_details", length = 2000)
    private String failureDetails;

    protected InventorySnapshotExecution() {
        // Required by JPA.
    }

    public InventorySnapshotExecution(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
        this.status = InventorySnapshotExecutionStatus.RUNNING;
        this.snapshotsCreated = 0;
    }

    @PrePersist
    protected void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }

    public void markSuccessful(int snapshotsCreated) {
        this.status = InventorySnapshotExecutionStatus.SUCCESS;
        this.snapshotsCreated = snapshotsCreated;
        this.completedAt = LocalDateTime.now();
        this.failureDetails = null;
    }

    public void markFailed(String failureDetails) {
        this.status = InventorySnapshotExecutionStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.failureDetails = failureDetails;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public InventorySnapshotExecutionStatus getStatus() {
        return status;
    }

    public Integer getSnapshotsCreated() {
        return snapshotsCreated;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getFailureDetails() {
        return failureDetails;
    }
}
