package com.tradeflow.api.product;


import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class ProductSpecification {

    private ProductSpecification() {
    }

    public static Specification<Product> hasKeyword(String keyword) {
        return (root, query, criteriaBuilder) -> {
            if (keyword == null || keyword.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            String searchKeyword = "%" + keyword.trim().toLowerCase() + "%";

            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    searchKeyword
            );
        };
    }

    public static Specification<Product> hasMinPrice(BigDecimal minPrice) {
        return (root, query, criteriaBuilder) -> {
            if (minPrice == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.greaterThanOrEqualTo(
                    root.<BigDecimal>get("unitPrice"),
                    minPrice
            );
        };
    }

    public static Specification<Product> hasMaxPrice(BigDecimal maxPrice) {
        return (root, query, criteriaBuilder) -> {
            if (maxPrice == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.lessThanOrEqualTo(
                    root.<BigDecimal>get("unitPrice"),
                    maxPrice
            );
        };
    }

    public static Specification<Product> hasMinQuantity(Integer minQuantity) {
        return (root, query, criteriaBuilder) -> {
            if (minQuantity == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.greaterThanOrEqualTo(
                    root.<Integer>get("availableQuantity"),
                    minQuantity
            );
        };
    }

    public static Specification<Product> isLowStock() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(
                        root.<Integer>get("availableQuantity"),
                        root.<Integer>get("lowStockThreshold")
                );
    }

    public static Specification<Product> hasStockStatus(String status) {
        return (root, query, criteriaBuilder) -> {

            if (status == null || status.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            return switch (status.trim().toUpperCase()) {

                case "OUT_OF_STOCK" ->
                        criteriaBuilder.equal(
                                root.get("availableQuantity"), 0
                        );

                case "LOW_STOCK" ->
                        criteriaBuilder.and(
                                criteriaBuilder.greaterThan(
                                        root.<Integer>get("availableQuantity"), 0
                                ),
                                criteriaBuilder.lessThanOrEqualTo(
                                        root.<Integer>get("availableQuantity"),
                                        root.<Integer>get("lowStockThreshold")
                                )
                        );

                default -> throw new IllegalArgumentException(
                        "Invalid stock status"
                );
            };
        };
    }

}
