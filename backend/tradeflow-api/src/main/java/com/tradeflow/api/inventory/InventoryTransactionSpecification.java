package com.tradeflow.api.inventory;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class InventoryTransactionSpecification {

    private InventoryTransactionSpecification() {
    }

    public static Specification<InventoryTransaction> hasProductId(Long productId) {
        return (root, query, criteriaBuilder) -> {
            if (productId == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("product").get("id"),
                    productId
            );
        };
    }

    public static Specification<InventoryTransaction> hasTransactionType(
            InventoryTransactionType transactionType
    ) {
        return (root, query, criteriaBuilder) -> {
            if (transactionType == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("transactionType"),
                    transactionType
            );
        };
    }

    public static Specification<InventoryTransaction> hasStartDate(
            LocalDateTime startDate
    ) {
        return (root, query, criteriaBuilder) -> {
            if (startDate == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.greaterThanOrEqualTo(
                    root.get("createdAt"),
                    startDate
            );
        };
    }

    public static Specification<InventoryTransaction> hasEndDate(
            LocalDateTime endDate
    ) {
        return (root, query, criteriaBuilder) -> {
            if (endDate == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.lessThanOrEqualTo(
                    root.get("createdAt"),
                    endDate
            );
        };
    }
}