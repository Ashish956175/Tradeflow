-- TradeFlow V9: Inventory snapshot execution audit

CREATE TABLE inventory_snapshot_executions (
    id BIGINT NOT NULL AUTO_INCREMENT,

    snapshot_date DATE NOT NULL,

    status VARCHAR(20) NOT NULL,

    snapshots_created INT NOT NULL DEFAULT 0,

    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    completed_at DATETIME(6) DEFAULT NULL,

    failure_details VARCHAR(2000) DEFAULT NULL,

    PRIMARY KEY (id),

    CONSTRAINT chk_inventory_snapshot_execution_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),

    CONSTRAINT chk_inventory_snapshot_execution_count
        CHECK (snapshots_created >= 0),

    INDEX idx_snapshot_execution_date (snapshot_date),
    INDEX idx_snapshot_execution_status (status),
    INDEX idx_snapshot_execution_started (started_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
