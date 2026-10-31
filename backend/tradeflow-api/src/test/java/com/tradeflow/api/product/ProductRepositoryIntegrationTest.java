package com.tradeflow.api.product;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;



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
}