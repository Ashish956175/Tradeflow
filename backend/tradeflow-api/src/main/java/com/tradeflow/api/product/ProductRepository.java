package com.tradeflow.api.product;

import com.tradeflow.api.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository
        extends JpaRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    boolean existsBySku(String sku);

    @Modifying
    @Query("""
            UPDATE Product p
            SET p.availableQuantity = p.availableQuantity - :quantity
            WHERE p.id = :productId
              AND p.availableQuantity >= :quantity
            """)
    int reserveInventory(
            @Param("productId") Long productId,
            @Param("quantity") int quantity
    );

    @Modifying
    @Query("""
            UPDATE Product p
            SET p.availableQuantity = p.availableQuantity + :quantity
            WHERE p.id = :productId
            """)
    int releaseInventory(
            @Param("productId") Long productId,
            @Param("quantity") int quantity
    );
}
