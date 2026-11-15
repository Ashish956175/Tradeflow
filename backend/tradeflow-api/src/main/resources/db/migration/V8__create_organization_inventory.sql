-- TradeFlow V8: Organization-owned inventory

CREATE TABLE organization_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    on_hand_quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,

    average_unit_cost DECIMAL(14,4) NOT NULL DEFAULT 0.0000,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT uk_organization_inventory
        UNIQUE (organization_id, product_id),

    CONSTRAINT fk_org_inventory_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_org_inventory_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_org_inventory_on_hand
        CHECK (on_hand_quantity >= 0),

    CONSTRAINT chk_org_inventory_reserved
        CHECK (reserved_quantity >= 0),

    CONSTRAINT chk_org_inventory_available
        CHECK (reserved_quantity <= on_hand_quantity),

    CONSTRAINT chk_org_inventory_cost
        CHECK (average_unit_cost >= 0),

    INDEX idx_org_inventory_organization (organization_id),
    INDEX idx_org_inventory_product (product_id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE organization_inventory_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    purchase_order_id BIGINT DEFAULT NULL,
    performed_by BIGINT DEFAULT NULL,

    transaction_type VARCHAR(30) NOT NULL,

    quantity INT NOT NULL,

    previous_on_hand INT NOT NULL,
    new_on_hand INT NOT NULL,

    previous_reserved INT NOT NULL,
    new_reserved INT NOT NULL,

    unit_cost DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    total_cost DECIMAL(18,4) NOT NULL DEFAULT 0.0000,

    reason VARCHAR(500) NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_org_inventory_tx_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_org_inventory_tx_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_org_inventory_tx_order
        FOREIGN KEY (purchase_order_id)
        REFERENCES purchase_orders(id)
        ON DELETE SET NULL,

    CONSTRAINT fk_org_inventory_tx_user
        FOREIGN KEY (performed_by)
        REFERENCES users(id)
        ON DELETE SET NULL,

    CONSTRAINT chk_org_inventory_tx_type
        CHECK (transaction_type IN (
            'STOCK_IN',
            'STOCK_OUT',
            'RESERVED',
            'RELEASED',
            'SHIPPED',
            'GRN_ACCEPTED',
            'ADJUSTMENT'
        )),

    CONSTRAINT chk_org_inventory_tx_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_org_inventory_tx_on_hand
        CHECK (previous_on_hand >= 0 AND new_on_hand >= 0),

    CONSTRAINT chk_org_inventory_tx_reserved
        CHECK (previous_reserved >= 0 AND new_reserved >= 0),

    CONSTRAINT chk_org_inventory_tx_cost
        CHECK (unit_cost >= 0 AND total_cost >= 0),

    INDEX idx_org_inventory_tx_organization (organization_id),
    INDEX idx_org_inventory_tx_product (product_id),
    INDEX idx_org_inventory_tx_order (purchase_order_id),
    INDEX idx_org_inventory_tx_user (performed_by),
    INDEX idx_org_inventory_tx_created (created_at),
    INDEX idx_org_inventory_tx_type (transaction_type)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,

    organization_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,

    snapshot_date DATE NOT NULL,

    on_hand_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL,

    average_unit_cost DECIMAL(14,4) NOT NULL,
    inventory_value DECIMAL(18,4) NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT uk_inventory_snapshot
        UNIQUE (organization_id, product_id, snapshot_date),

    CONSTRAINT fk_inventory_snapshot_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_inventory_snapshot_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_inventory_snapshot_quantity
        CHECK (
            on_hand_quantity >= 0
            AND reserved_quantity >= 0
            AND reserved_quantity <= on_hand_quantity
        ),

    CONSTRAINT chk_inventory_snapshot_cost
        CHECK (average_unit_cost >= 0 AND inventory_value >= 0),

    INDEX idx_inventory_snapshot_date (snapshot_date),
    INDEX idx_inventory_snapshot_organization (organization_id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;