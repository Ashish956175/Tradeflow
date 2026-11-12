-- TradeFlow V7: Organization memberships and membership capabilities

CREATE TABLE organization_memberships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    membership_role ENUM('OWNER', 'ADMIN', 'MEMBER') NOT NULL,
    status ENUM('INVITED', 'ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'INVITED',
    joined_at DATETIME(6) DEFAULT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_membership_user_organization
        UNIQUE (user_id, organization_id),

    KEY idx_membership_organization_status (organization_id, status),
    KEY idx_membership_user_status (user_id, status),

    CONSTRAINT fk_membership_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_membership_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE membership_capabilities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    membership_id BIGINT NOT NULL,
    capability ENUM('BUYER', 'SUPPLIER') NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_membership_capability
        UNIQUE (membership_id, capability),

    CONSTRAINT fk_membership_capability_membership
        FOREIGN KEY (membership_id)
        REFERENCES organization_memberships (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Migrate legacy organization-user relationships.
-- The earliest-created user in each organization becomes OWNER.

INSERT INTO organization_memberships (
    user_id,
    organization_id,
    membership_role,
    status,
    joined_at
)
SELECT
    ranked.user_id,
    ranked.organization_id,
    CASE
        WHEN ranked.membership_rank = 1 THEN 'OWNER'
        ELSE 'MEMBER'
    END,
    CASE
        WHEN ranked.enabled = 1 THEN 'ACTIVE'
        ELSE 'SUSPENDED'
    END,
    ranked.created_at
FROM (
    SELECT
        u.id AS user_id,
        u.organization_id,
        u.created_at,
        u.enabled,
        ROW_NUMBER() OVER (
            PARTITION BY u.organization_id
            ORDER BY u.enabled DESC, u.created_at ASC, u.id ASC
        ) AS membership_rank
    FROM users u
    WHERE u.organization_id IS NOT NULL
      AND u.role IN ('BUYER', 'SUPPLIER')
) ranked;


-- Migrate explicit capabilities for non-owner memberships.
-- Owners inherit all capabilities enabled for their organization.

INSERT INTO membership_capabilities (
    membership_id,
    capability
)
SELECT
    om.id,
    u.role
FROM organization_memberships om
JOIN users u
    ON u.id = om.user_id
JOIN organization_capabilities oc
    ON oc.organization_id = om.organization_id
   AND oc.capability = u.role
WHERE om.membership_role <> 'OWNER'
  AND u.role IN ('BUYER', 'SUPPLIER');
