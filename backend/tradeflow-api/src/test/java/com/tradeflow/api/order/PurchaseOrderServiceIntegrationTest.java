package com.tradeflow.api.order;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationRepository;
import com.tradeflow.api.product.Product;
import com.tradeflow.api.product.ProductRepository;
import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.procurement.ProcurementRequestRepository;
import com.tradeflow.api.quotation.Quotation;
import com.tradeflow.api.quotation.QuotationRepository;
import com.tradeflow.api.supplier.Supplier;
import com.tradeflow.api.supplier.SupplierRepository;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "JWT_SECRET=VGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWtleS1mb3ItdHJhZGVmbG93"
})
@Testcontainers
class PurchaseOrderServiceIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@tradeflow.test";

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("tradeflow_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withCommand("--log-bin-trust-function-creators=1");

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
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProcurementRequestRepository procurementRequestRepository;

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private record TestData(Long productId, Long orderId) {
    }

    private TestData createTestData(int availableQuantity) {
        String testId = String.valueOf(System.nanoTime());
        return transactionTemplate.execute(status -> {

            User admin = userRepository.findByEmail(ADMIN_EMAIL)
                    .orElseGet(() -> userRepository.saveAndFlush(
                            new User(
                                    "Test Administrator",
                                    ADMIN_EMAIL,
                                    "test-password-hash",
                                    UserRole.ADMIN
                            )
                    ));

            userRepository.saveAndFlush(admin);

            Organization buyer = new Organization();
            buyer.setName("Test Buyer");
            buyer.setBusinessEmail("buyer-" + testId + "@tradeflow.test");

            organizationRepository.saveAndFlush(buyer);

            Organization supplierOrganization = new Organization();
            supplierOrganization.setName("Test Supplier Organization");
            supplierOrganization.setBusinessEmail(
                    "supplier-org-" + testId + "@tradeflow.test"
            );


            organizationRepository.saveAndFlush(supplierOrganization);

            Supplier supplier = new Supplier();
            supplier.setOrganization(supplierOrganization);
            supplier.setName("Test Supplier");
            supplier.setEmail("supplier-" + testId + "@tradeflow.test");


            supplierRepository.saveAndFlush(supplier);

            Product product = new Product();
            product.setName("Integration Test Product");
            product.setDescription("Purchase order integration test");
            product.setSku("TEST-" + System.nanoTime());
            product.setUnitPrice(new BigDecimal("100.00"));
            product.setAvailableQuantity(availableQuantity);
            product.setUnit("PIECE");

            productRepository.saveAndFlush(product);

            ProcurementRequest request = new ProcurementRequest(
                    buyer,
                    product,
                    80,
                    new BigDecimal("100.00"),
                    "Test delivery address",
                    LocalDate.now().plusDays(30)
            );

            procurementRequestRepository.saveAndFlush(request);

            Quotation quotation = new Quotation(
                    request,
                    supplier,
                    new BigDecimal("100.00"),
                    80,
                    LocalDate.now().plusDays(20)
            );

            quotation.accept();

            quotationRepository.saveAndFlush(quotation);

            PurchaseOrder order = new PurchaseOrder(
                    quotation,
                    "Test delivery address"
            );

            purchaseOrderRepository.saveAndFlush(order);

            return new TestData(product.getId(), order.getId());
        });
    }

    @Test
    void confirmOrder_shouldReserveInventoryAndPersistConfirmedStatus() {

        TestData data = createTestData(100);

        PurchaseOrder confirmedOrder =
                purchaseOrderService.confirmOrder(
                        data.orderId(),
                        ADMIN_EMAIL
                );

        Product updatedProduct = productRepository
                .findById(data.productId())
                .orElseThrow();

        PurchaseOrder persistedOrder = purchaseOrderRepository
                .findById(data.orderId())
                .orElseThrow();

        assertEquals(OrderStatus.CONFIRMED, confirmedOrder.getStatus());
        assertEquals(OrderStatus.CONFIRMED, persistedOrder.getStatus());
        assertEquals(20, updatedProduct.getAvailableQuantity());
    }

    @Test
    void cancelConfirmedOrder_shouldReleaseInventoryAndPersistCancelledStatus() {

        TestData data = createTestData(100);

        // First confirm the order: inventory 100 -> 20.
        purchaseOrderService.confirmOrder(
                data.orderId(),
                ADMIN_EMAIL
        );

        // Cancel the confirmed order: inventory 20 -> 100.
        PurchaseOrder cancelledOrder =
                purchaseOrderService.cancelOrder(
                        data.orderId(),
                        ADMIN_EMAIL
                );

        Product updatedProduct = productRepository
                .findById(data.productId())
                .orElseThrow();

        PurchaseOrder persistedOrder = purchaseOrderRepository
                .findById(data.orderId())
                .orElseThrow();

        assertEquals(OrderStatus.CANCELLED, cancelledOrder.getStatus());
        assertEquals(OrderStatus.CANCELLED, persistedOrder.getStatus());
        assertEquals(100, updatedProduct.getAvailableQuantity());
    }

    @Test
    void confirmOrder_withInsufficientInventory_shouldNotChangeOrderOrInventory() {

        TestData data = createTestData(50);

        org.springframework.web.server.ResponseStatusException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.springframework.web.server.ResponseStatusException.class,
                        () -> purchaseOrderService.confirmOrder(
                                data.orderId(),
                                ADMIN_EMAIL
                        )
                );

        Product product = productRepository
                .findById(data.productId())
                .orElseThrow();

        PurchaseOrder order = purchaseOrderRepository
                .findById(data.orderId())
                .orElseThrow();

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(50, product.getAvailableQuantity());
        assertEquals(OrderStatus.CREATED, order.getStatus());
    }

    @Test
    void cancelOrder_whenDatabaseUpdateFails_shouldRollbackInventoryRelease() {

        TestData data = createTestData(100);

        // Confirm order: inventory decreases from 100 to 20.
        purchaseOrderService.confirmOrder(
                data.orderId(),
                ADMIN_EMAIL
        );

        // Create a trigger that deliberately rejects purchase-order updates.
        jdbcTemplate.execute("""
            CREATE TRIGGER test_fail_order_update
            BEFORE UPDATE ON purchase_orders
            FOR EACH ROW
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Simulated database failure'
            """);

        try {
            // Cancellation should fail during transaction commit.
            assertThrows(
                    RuntimeException.class,
                    () -> purchaseOrderService.cancelOrder(
                            data.orderId(),
                            ADMIN_EMAIL
                    )
            );

        } finally {
            // Always remove the test trigger.
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS test_fail_order_update"
            );
        }

        // Verify that the complete transaction was rolled back.
        Product product = productRepository
                .findById(data.productId())
                .orElseThrow();

        PurchaseOrder order = purchaseOrderRepository
                .findById(data.orderId())
                .orElseThrow();

        assertEquals(20, product.getAvailableQuantity());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }
}