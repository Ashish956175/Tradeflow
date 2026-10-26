-- TradeFlow V1: Initial database schema
-- Target database: MySQL 8.x
-- Used to initialize a fresh TradeFlow database.

CREATE TABLE organizations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    address VARCHAR(500),
    business_email VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(20),
    PRIMARY KEY (id),
    CONSTRAINT uk_organization_business_email UNIQUE (business_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    available_quantity INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    description VARCHAR(1000),
    name VARCHAR(150) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_sku UNIQUE (sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    email VARCHAR(150) NOT NULL,
    enabled BIT(1) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('ADMIN','BUYER','SUPPLIER') NOT NULL,
    organization_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT fk_user_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE suppliers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    address VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(20),
    organization_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_supplier_organization_email
        UNIQUE (organization_id, email),
    CONSTRAINT fk_supplier_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE buyer_supplier_relationships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    status ENUM('ACTIVE','INACTIVE','PENDING','REJECTED') NOT NULL,
    buyer_organization_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_buyer_supplier
        UNIQUE (buyer_organization_id, supplier_id),
    CONSTRAINT fk_relationship_buyer_organization
        FOREIGN KEY (buyer_organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_relationship_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE procurement_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    requested_quantity INT NOT NULL,
    required_delivery_date DATE,
    status ENUM(
        'ACCEPTED','CANCELLED','COMPLETED','OPEN','QUOTATIONS_RECEIVED'
    ) NOT NULL,
    target_unit_price DECIMAL(12,2),
    buyer_organization_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_procurement_buyer_organization
        FOREIGN KEY (buyer_organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_procurement_product
        FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quotations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    estimated_delivery_date DATE NOT NULL,
    offered_quantity INT NOT NULL,
    offered_unit_price DECIMAL(12,2) NOT NULL,
    status ENUM('ACCEPTED','REJECTED','SUBMITTED','WITHDRAWN') NOT NULL,
    procurement_request_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quotation_request_supplier
        UNIQUE (procurement_request_id, supplier_id),
    CONSTRAINT fk_quotation_procurement_request
        FOREIGN KEY (procurement_request_id) REFERENCES procurement_requests(id),
    CONSTRAINT fk_quotation_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    agreed_unit_price DECIMAL(12,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    expected_delivery_date DATE NOT NULL,
    order_number VARCHAR(40) NOT NULL,
    ordered_quantity INT NOT NULL,
    status ENUM('CANCELLED','CONFIRMED','CREATED','DELIVERED','SHIPPED') NOT NULL,
    total_amount DECIMAL(14,2) NOT NULL,
    quotation_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_order_number UNIQUE (order_number),
    CONSTRAINT uk_purchase_order_quotation UNIQUE (quotation_id),
    CONSTRAINT fk_purchase_order_quotation
        FOREIGN KEY (quotation_id) REFERENCES quotations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    amount DECIMAL(14,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    payment_method VARCHAR(50),
    status ENUM('COMPLETED','FAILED','PENDING','PROCESSING','REFUNDED') NOT NULL,
    transaction_reference VARCHAR(100),
    updated_at DATETIME(6) NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_purchase_order UNIQUE (purchase_order_id),
    CONSTRAINT uk_payment_transaction_reference UNIQUE (transaction_reference),
    CONSTRAINT fk_payment_purchase_order
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shipments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actual_delivery_date DATE,
    carrier_name VARCHAR(100),
    created_at DATETIME(6) NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    estimated_delivery_date DATE,
    status ENUM(
        'CREATED','DELIVERED','FAILED_DELIVERY','IN_TRANSIT',
        'OUT_FOR_DELIVERY','PICKED_UP','RETURNED'
    ) NOT NULL,
    tracking_number VARCHAR(100),
    updated_at DATETIME(6) NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_shipment_tracking_number UNIQUE (tracking_number),
    CONSTRAINT uk_shipment_purchase_order UNIQUE (purchase_order_id),
    CONSTRAINT fk_shipment_purchase_order
        FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;