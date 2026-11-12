-- TradeFlow V6: Organization capabilities and capability audit

CREATE TABLE organization_capabilities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    capability ENUM('BUYER', 'SUPPLIER') NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT uk_organization_capability
        UNIQUE (organization_id, capability),

    CONSTRAINT fk_organization_capability_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE organization_capability_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    capability ENUM('BUYER', 'SUPPLIER') NOT NULL,
    action ENUM('ENABLED', 'DISABLED') NOT NULL,
    changed_by_user_id BIGINT DEFAULT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    KEY idx_capability_audit_organization (organization_id),
    KEY idx_capability_audit_actor (changed_by_user_id),

    CONSTRAINT fk_capability_audit_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id),

    CONSTRAINT fk_capability_audit_actor
        FOREIGN KEY (changed_by_user_id)
        REFERENCES users (id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Migrate existing organization capabilities from legacy user roles.

INSERT INTO organization_capabilities (
    organization_id,
    capability
)
SELECT DISTINCT
    organization_id,
    role
FROM users
WHERE organization_id IS NOT NULL
  AND role IN ('BUYER', 'SUPPLIER');


-- Record the initial capability migration in the audit history.

INSERT INTO organization_capability_audit (
    organization_id,
    capability,
    action,
    changed_by_user_id,
    reason
)
SELECT DISTINCT
    organization_id,
    role,
    'ENABLED',
    NULL,
    'Initial capability migration from legacy user role'
FROM users
WHERE organization_id IS NOT NULL
  AND role IN ('BUYER', 'SUPPLIER');
