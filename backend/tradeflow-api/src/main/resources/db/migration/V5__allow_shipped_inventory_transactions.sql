ALTER TABLE inventory_transactions
    DROP CHECK chk_inventory_transaction_type;

ALTER TABLE inventory_transactions
    ADD CONSTRAINT chk_inventory_transaction_type
    CHECK (transaction_type IN (
        'STOCK_IN',
        'STOCK_OUT',
        'SHIPPED',
        'RESERVED',
        'RELEASED',
        'ADJUSTMENT'
    ));