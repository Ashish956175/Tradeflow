CREATE TABLE refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    amount DECIMAL(14,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status ENUM(
        'REQUESTED',
        'PROCESSING',
        'COMPLETED',
        'FAILED',
        'CANCELLED'
    ) NOT NULL,
    failure_reason VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    payment_id BIGINT NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_refund_payment
        FOREIGN KEY (payment_id) REFERENCES payments(id),

    INDEX idx_refund_payment_id (payment_id),
    INDEX idx_refund_status (status),

    CONSTRAINT chk_refund_amount_positive
        CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
