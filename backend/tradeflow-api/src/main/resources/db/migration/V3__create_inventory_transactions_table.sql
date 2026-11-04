CREATE TABLE inventory_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    purchase_order_id BIGINT NULL,
    performed_by BIGINT NOT NULL,

    transaction_type VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    previous_quantity INT NOT NULL,
    new_quantity INT NOT NULL,

    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_inventory_transaction_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT fk_inventory_transaction_purchase_order
        FOREIGN KEY (purchase_order_id)
        REFERENCES purchase_orders(id),

    CONSTRAINT fk_inventory_transaction_user
        FOREIGN KEY (performed_by)
        REFERENCES users(id),

    CONSTRAINT chk_inventory_transaction_type
        CHECK (transaction_type IN (
            'STOCK_IN',
            'STOCK_OUT',
            'RESERVED',
            'RELEASED',
            'ADJUSTMENT'
        )),

    CONSTRAINT chk_inventory_transaction_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_inventory_transaction_previous_quantity
        CHECK (previous_quantity >= 0),

    CONSTRAINT chk_inventory_transaction_new_quantity
        CHECK (new_quantity >= 0),

    INDEX idx_inventory_transaction_product (product_id),
    INDEX idx_inventory_transaction_purchase_order (purchase_order_id),
    INDEX idx_inventory_transaction_user (performed_by),
    INDEX idx_inventory_transaction_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
