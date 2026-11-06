package com.tradeflow.api.product;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "JWT_SECRET=VGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWtleS1mb3ItdHJhZGVmbG93"
})
@Testcontainers
class ProductRepositoryIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("tradeflow_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name",
                mysql::getDriverClassName);
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private Product createProduct(int quantity) {

        Product product = new Product();

        product.setName("Test Product");
        product.setDescription("Inventory integration test");
        product.setSku("TEST-" + System.nanoTime());
        product.setUnitPrice(new BigDecimal("100.00"));
        product.setAvailableQuantity(quantity);
        product.setUnit("PIECE");

        return productRepository.saveAndFlush(product);
    }

    private Product createProduct(int quantity, int threshold) {

        Product product = new Product();

        product.setName("Threshold Test Product");
        product.setDescription("Low-stock specification integration test");
        product.setSku("THRESHOLD-" + System.nanoTime());
        product.setUnitPrice(new BigDecimal("100.00"));
        product.setAvailableQuantity(quantity);
        product.setLowStockThreshold(threshold);
        product.setUnit("PIECE");

        return productRepository.saveAndFlush(product);
    }

    @Test
    @Transactional
    void reserveInventory_shouldReduceAvailableQuantity() {

        Product product = createProduct(100);

        int updatedRows = productRepository.reserveInventory(
                product.getId(), 20
        );

        entityManager.clear();

        Product updatedProduct =
                productRepository.findById(product.getId()).orElseThrow();

        assertEquals(1, updatedRows);
        assertEquals(80, updatedProduct.getAvailableQuantity());
    }

    @Test
    @Transactional
    void releaseInventory_shouldIncreaseAvailableQuantity() {

        Product product = createProduct(80);

        int updatedRows = productRepository.releaseInventory(
                product.getId(), 20
        );

        entityManager.clear();

        Product updatedProduct =
                productRepository.findById(product.getId()).orElseThrow();

        assertEquals(1, updatedRows);
        assertEquals(100, updatedProduct.getAvailableQuantity());
    }

    @Test
    @Transactional
    void reserveInventory_withInsufficientStock_shouldNotUpdateQuantity() {

        Product product = createProduct(10);

        int updatedRows = productRepository.reserveInventory(
                product.getId(), 20
        );

        entityManager.clear();

        Product updatedProduct =
                productRepository.findById(product.getId()).orElseThrow();

        assertEquals(0, updatedRows);
        assertEquals(10, updatedProduct.getAvailableQuantity());
    }

    @Test
    @Transactional
    void reserveInventory_withNonexistentProduct_shouldReturnZero() {

        int updatedRows = productRepository.reserveInventory(
                999999L, 10
        );

        assertEquals(0, updatedRows);
    }
    @Test
    void reserveInventory_concurrentRequests_shouldPreventOverselling() throws Exception {

        Product product = createProduct(100);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Integer> firstReservation = executor.submit(() -> {
                ready.countDown();
                start.await();

                return transactionTemplate.execute(status ->
                        productRepository.reserveInventory(product.getId(), 80)
                );
            });

            Future<Integer> secondReservation = executor.submit(() -> {
                ready.countDown();
                start.await();

                return transactionTemplate.execute(status ->
                        productRepository.reserveInventory(product.getId(), 80)
                );
            });

            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));

            start.countDown();

            int firstResult = firstReservation.get();
            int secondResult = secondReservation.get();

            entityManager.clear();

            Product updatedProduct =
                    productRepository.findById(product.getId()).orElseThrow();

            assertEquals(1, firstResult + secondResult);
            assertEquals(20, updatedProduct.getAvailableQuantity());

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional
    void lowStockSpecification_shouldReturnCorrectProductsAndStatuses() {

        // Create products covering all stock conditions.
        Product belowThreshold = createProduct(5, 10);
        Product atThreshold = createProduct(10, 10);
        Product outOfStock = createProduct(0, 10);
        Product aboveThreshold = createProduct(11, 10);

        Specification<Product> lowStockSpecification =
                ProductSpecification.isLowStock();

        // Verify all low-stock and out-of-stock products.
        Page<Product> lowStockPage = productRepository.findAll(
                lowStockSpecification,
                PageRequest.of(
                        0,
                        10,
                        Sort.by(Sort.Direction.ASC, "availableQuantity")
                )
        );

        List<Long> lowStockIds = lowStockPage.getContent()
                .stream()
                .map(Product::getId)
                .toList();

        assertTrue(lowStockIds.contains(belowThreshold.getId()));
        assertTrue(lowStockIds.contains(atThreshold.getId()));
        assertTrue(lowStockIds.contains(outOfStock.getId()));

        assertTrue(!lowStockIds.contains(aboveThreshold.getId()));

        // Verify LOW_STOCK excludes out-of-stock products.
        Specification<Product> onlyLowStock =
                lowStockSpecification.and(
                        ProductSpecification.hasStockStatus("LOW_STOCK")
                );

        List<Product> lowStockProducts =
                productRepository.findAll(onlyLowStock);

        List<Long> onlyLowStockIds = lowStockProducts.stream()
                .map(Product::getId)
                .toList();

        assertTrue(onlyLowStockIds.contains(belowThreshold.getId()));
        assertTrue(onlyLowStockIds.contains(atThreshold.getId()));
        assertTrue(!onlyLowStockIds.contains(outOfStock.getId()));
        assertTrue(!onlyLowStockIds.contains(aboveThreshold.getId()));

        // Verify OUT_OF_STOCK returns only zero-quantity products.
        Specification<Product> onlyOutOfStock =
                lowStockSpecification.and(
                        ProductSpecification.hasStockStatus("OUT_OF_STOCK")
                );

        List<Product> outOfStockProducts =
                productRepository.findAll(onlyOutOfStock);

        List<Long> outOfStockIds = outOfStockProducts.stream()
                .map(Product::getId)
                .toList();

        assertTrue(outOfStockIds.contains(outOfStock.getId()));
        assertTrue(!outOfStockIds.contains(belowThreshold.getId()));
        assertTrue(!outOfStockIds.contains(atThreshold.getId()));
        assertTrue(!outOfStockIds.contains(aboveThreshold.getId()));
    }

    // --------------------------------------------------
    // Inventory dashboard summary integration tests
    // --------------------------------------------------

    @Test
    @Transactional
    void getInventorySummary_shouldReturnCorrectAggregatedMetrics() {

        // Ensure a predictable test dataset.
        productRepository.deleteAllInBatch();

        // Create products covering all stock classifications.
        createProduct(0, 10);
        createProduct(5, 10);
        createProduct(10, 10);
        createProduct(11, 10);

        InventorySummaryProjection summary =
                productRepository.getInventorySummary();

        assertEquals(4L, summary.getTotalProducts());
        assertEquals(26L, summary.getTotalInventoryUnits());
        assertEquals(2L, summary.getLowStockProducts());
        assertEquals(1L, summary.getOutOfStockProducts());
        assertEquals(1L, summary.getHealthyStockProducts());
    }

    @Test
    @Transactional
    void getInventorySummary_shouldReturnZerosWhenNoProductsExist() {

        productRepository.deleteAllInBatch();

        InventorySummaryProjection summary =
                productRepository.getInventorySummary();

        assertEquals(0L, summary.getTotalProducts());
        assertEquals(0L, summary.getTotalInventoryUnits());
        assertEquals(0L, summary.getLowStockProducts());
        assertEquals(0L, summary.getOutOfStockProducts());
        assertEquals(0L, summary.getHealthyStockProducts());
    }

    // --------------------------------------------------
// Inventory valuation integration tests
// --------------------------------------------------

    @Test
    @Transactional
    void getInventoryValuation_shouldReturnCorrectAggregatedValues() {

        // Ensure a predictable test dataset.
        productRepository.deleteAllInBatch();

        // Product 1: 10 units × ₹100 = ₹1,000.
        createProduct(10);

        // Product 2: 5 units × ₹50 = ₹250.
        Product secondProduct = createProduct(5);
        secondProduct.setUnitPrice(new BigDecimal("50.00"));
        productRepository.saveAndFlush(secondProduct);

        // Product 3: 0 units × ₹200 = ₹0.
        Product thirdProduct = createProduct(0);
        thirdProduct.setUnitPrice(new BigDecimal("200.00"));
        productRepository.saveAndFlush(thirdProduct);

        InventoryValuationProjection valuation =
                productRepository.getInventoryValuation();

        assertEquals(3L, valuation.getTotalProducts());
        assertEquals(15L, valuation.getTotalInventoryUnits());

        assertEquals(
                0,
                new BigDecimal("1250.00")
                        .compareTo(valuation.getTotalInventoryValue())
        );
    }

    @Test
    @Transactional
    void getInventoryValuation_shouldReturnZerosWhenNoProductsExist() {

        productRepository.deleteAllInBatch();

        InventoryValuationProjection valuation =
                productRepository.getInventoryValuation();

        assertEquals(0L, valuation.getTotalProducts());
        assertEquals(0L, valuation.getTotalInventoryUnits());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(valuation.getTotalInventoryValue())
        );
    }

}