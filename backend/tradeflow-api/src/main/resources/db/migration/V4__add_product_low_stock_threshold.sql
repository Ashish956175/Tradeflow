ALTER TABLE products
ADD COLUMN low_stock_threshold INT NOT NULL DEFAULT 10;

ALTER TABLE products
ADD CONSTRAINT chk_product_low_stock_threshold
CHECK (low_stock_threshold >= 1);