package com.tradeflow.api.inventory;

import com.tradeflow.api.product.Product;
import com.tradeflow.api.product.ProductRepository;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "JWT_SECRET=VGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWtleS1mb3ItdHJhZGVmbG93"
})
@Testcontainers
class InventoryTransactionRepositoryIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("tradeflow_inventory_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                mysql::getDriverClassName
        );
    }

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private Product createProduct() {
        Product product = new Product();
        product.setName("Analytics Test Product");
        product.setDescription("Inventory movement analytics test");
        product.setSku("ANALYTICS-" + System.nanoTime());
        product.setUnitPrice(new BigDecimal("100.00"));
        product.setAvailableQuantity(100);
        product.setUnit("PIECE");

        return productRepository.saveAndFlush(product);
    }

    private User createAdmin() {
        User admin = new User(
                "Analytics Admin",
                "analytics-" + System.nanoTime() + "@tradeflow.test",
                "test-password-hash",
                UserRole.ADMIN
        );

        return userRepository.saveAndFlush(admin);
    }

    private void createTransaction(
            Product product,
            User admin,
            InventoryTransactionType type,
            int quantity,
            int previousQuantity,
            int newQuantity
    ) {
        InventoryTransaction transaction = new InventoryTransaction(
                product,
                null,
                admin,
                type,
                quantity,
                previousQuantity,
                newQuantity,
                "Analytics integration test"
        );

        transactionRepository.save(transaction);
    }

    @Test
    @Transactional
    void getInventoryMovementAnalytics_shouldAggregatePhysicalMovements() {

        Product product = createProduct();
        User admin = createAdmin();

        createTransaction(
                product, admin,
                InventoryTransactionType.STOCK_IN,
                100, 0, 100
        );

        createTransaction(
                product, admin,
                InventoryTransactionType.STOCK_OUT,
                30, 100, 70
        );
        createTransaction(
                product, admin,
                InventoryTransactionType.SHIPPED,
                15, 80, 80
        );

        createTransaction(
                product, admin,
                InventoryTransactionType.ADJUSTMENT,
                20, 70, 90
        );

        createTransaction(
                product, admin,
                InventoryTransactionType.ADJUSTMENT,
                10, 90, 80
        );

        createTransaction(
                product, admin,
                InventoryTransactionType.RESERVED,
                5, 80, 75
        );

        createTransaction(
                product, admin,
                InventoryTransactionType.RELEASED,
                5, 75, 80
        );

        transactionRepository.flush();

        InventoryMovementAnalyticsProjection analytics =
                transactionRepository.getInventoryMovementAnalytics(
                        null,
                        null
                );

        assertEquals(100L, analytics.getTotalStockIn());
        assertEquals(45L, analytics.getTotalStockOut());
        assertEquals(20L, analytics.getAdjustmentIncrease());
        assertEquals(10L, analytics.getAdjustmentDecrease());

        // STOCK_IN, STOCK_OUT, SHIPPED and ADJUSTMENT are counted.
        // RESERVED and RELEASED are excluded.
        assertEquals(5L, analytics.getTotalTransactions());
    }

    @Test
    @Transactional
    void getInventoryMovementAnalytics_shouldReturnZerosForEmptyDateRange() {

        LocalDateTime startDate = LocalDateTime.now().plusDays(10);
        LocalDateTime endDate = startDate.plusDays(1);

        InventoryMovementAnalyticsProjection analytics =
                transactionRepository.getInventoryMovementAnalytics(
                        startDate,
                        endDate
                );

        assertEquals(0L, analytics.getTotalStockIn());
        assertEquals(0L, analytics.getTotalStockOut());
        assertEquals(0L, analytics.getAdjustmentIncrease());
        assertEquals(0L, analytics.getAdjustmentDecrease());
        assertEquals(0L, analytics.getTotalTransactions());
    }
}
