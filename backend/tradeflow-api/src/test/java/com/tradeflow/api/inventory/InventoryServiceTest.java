package com.tradeflow.api.inventory;
import com.tradeflow.api.product.InventorySummaryProjection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import com.tradeflow.api.product.Product;
import com.tradeflow.api.product.InventorySummaryProjection;

import com.tradeflow.api.product.ProductRepository;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;



import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final Long PRODUCT_ID = 1L;
    private static final String ADMIN_EMAIL = "admin@test.com";

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User admin;

    @Mock
    private User buyer;

    @Mock
    private User supplier;

    @Mock
    private Product product;

    @InjectMocks
    private InventoryService inventoryService;

    // --------------------------------------------------
    // Authentication helpers
    // --------------------------------------------------

    private void authenticateAdmin() {
        when(admin.isEnabled()).thenReturn(true);
        when(admin.getRole()).thenReturn(UserRole.ADMIN);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(admin));
    }

    private InventoryTransaction createMockTransaction() {

        InventoryTransaction transaction = mock(InventoryTransaction.class);

        when(product.getId()).thenReturn(PRODUCT_ID);
        when(product.getName()).thenReturn("Industrial Safety Gloves");
        when(product.getSku()).thenReturn("TF-GLOVE-001");

        when(admin.getId()).thenReturn(3L);
        when(admin.getEmail()).thenReturn(ADMIN_EMAIL);

        when(transaction.getId()).thenReturn(1L);
        when(transaction.getProduct()).thenReturn(product);
        when(transaction.getPurchaseOrder()).thenReturn(null);
        when(transaction.getPerformedBy()).thenReturn(admin);
        when(transaction.getTransactionType())
                .thenReturn(InventoryTransactionType.STOCK_IN);
        when(transaction.getQuantity()).thenReturn(100);
        when(transaction.getPreviousQuantity()).thenReturn(1950);
        when(transaction.getNewQuantity()).thenReturn(2050);
        when(transaction.getReason()).thenReturn("New stock received");
        when(transaction.getCreatedAt())
                .thenReturn(LocalDateTime.of(2026, 11, 5, 10, 30));

        return transaction;
    }

    // --------------------------------------------------
// Inventory transaction history tests
// --------------------------------------------------

    @Test
    void getTransactionHistory_shouldReturnPaginatedTransactionHistory() {

        authenticateAdmin();

        InventoryTransaction transaction = createMockTransaction();

        Page<InventoryTransaction> transactionPage = new PageImpl<>(
                List.of(transaction),
                PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt")),
                11
        );

        when(transactionRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(transactionPage);

        Page<InventoryTransactionResponse> response =
                inventoryService.getTransactionHistory(
                        1,
                        5,
                        "createdAt",
                        "desc",
                        null,
                        null,
                        null,
                        null,
                        ADMIN_EMAIL
                );

        // Verify pagination metadata.
        assertNotNull(response);
        assertEquals(1, response.getNumber());
        assertEquals(5, response.getSize());
        assertEquals(11, response.getTotalElements());
        assertEquals(3, response.getTotalPages());

        // Verify response DTO mapping.
        InventoryTransactionResponse result = response.getContent().get(0);

        assertEquals(1L, result.id());
        assertEquals(PRODUCT_ID, result.productId());
        assertEquals("Industrial Safety Gloves", result.productName());
        assertEquals("TF-GLOVE-001", result.productSku());
        assertNull(result.purchaseOrderId());
        assertEquals(3L, result.performedById());
        assertEquals(ADMIN_EMAIL, result.performedByEmail());
        assertEquals("STOCK_IN", result.transactionType());
        assertEquals(100, result.quantity());
        assertEquals(1950, result.previousQuantity());
        assertEquals(2050, result.newQuantity());
        assertEquals("New stock received", result.reason());

        assertEquals(
                LocalDateTime.of(2026, 11, 5, 10, 30),
                result.createdAt()
        );

        // Verify the repository received the correct pagination and sorting.
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(transactionRepository).findAll(
                any(Specification.class),
                pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();

        assertEquals(1, pageable.getPageNumber());
        assertEquals(5, pageable.getPageSize());
        assertEquals(
                Sort.Direction.DESC,
                pageable.getSort().getOrderFor("createdAt").getDirection()
        );
    }

    @Test
    void getTransactionHistory_shouldApplyCombinedFilters() {

        authenticateAdmin();

        LocalDateTime startDate = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 11, 5, 23, 59);

        when(transactionRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<InventoryTransactionResponse> response =
                inventoryService.getTransactionHistory(
                        0,
                        20,
                        "quantity",
                        "asc",
                        PRODUCT_ID,
                        InventoryTransactionType.STOCK_OUT,
                        startDate,
                        endDate,
                        ADMIN_EMAIL
                );

        assertNotNull(response);
        assertTrue(response.isEmpty());

        ArgumentCaptor<Specification<InventoryTransaction>> specificationCaptor =
                ArgumentCaptor.forClass(Specification.class);

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(transactionRepository).findAll(
                specificationCaptor.capture(),
                pageableCaptor.capture()
        );

        assertNotNull(specificationCaptor.getValue());

        Pageable pageable = pageableCaptor.getValue();

        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(
                Sort.Direction.ASC,
                pageable.getSort().getOrderFor("quantity").getDirection()
        );
    }

    @Test
    void getTransactionHistory_shouldRejectNegativePage() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getTransactionHistory(
                        -1, 10, "createdAt", "desc",
                        null, null, null, null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactionHistory_shouldRejectInvalidPageSize() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getTransactionHistory(
                        0, 0, "createdAt", "desc",
                        null, null, null, null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactionHistory_shouldRejectPageSizeAboveMaximum() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getTransactionHistory(
                        0, 101, "createdAt", "desc",
                        null, null, null, null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactionHistory_shouldRejectInvalidSortField() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getTransactionHistory(
                        0, 10, "password", "desc",
                        null, null, null, null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactionHistory_shouldRejectInvalidSortDirection() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getTransactionHistory(
                        0, 10, "createdAt", "random",
                        null, null, null, null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactionHistory_shouldRejectInvalidProductId() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getTransactionHistory(
                        0, 10, "createdAt", "desc",
                        0L, null, null, null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactionHistory_shouldRejectInvalidDateRange() {

        authenticateAdmin();

        LocalDateTime startDate = LocalDateTime.of(2026, 11, 6, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 11, 5, 0, 0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getTransactionHistory(
                        0, 10, "createdAt", "desc",
                        null, null, startDate, endDate, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactionHistory_shouldRejectBuyer() {

        when(buyer.isEnabled()).thenReturn(true);
        when(buyer.getRole()).thenReturn(UserRole.BUYER);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(buyer));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getTransactionHistory(
                        0, 10, "createdAt", "desc",
                        null, null, null, null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactionHistory_shouldRejectSupplier() {

        when(supplier.isEnabled()).thenReturn(true);
        when(supplier.getRole()).thenReturn(UserRole.SUPPLIER);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(supplier));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getTransactionHistory(
                        0, 10, "createdAt", "desc",
                        null, null, null, null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactionHistory_shouldRejectDisabledAdmin() {

        when(admin.isEnabled()).thenReturn(false);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(admin));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getTransactionHistory(
                        0, 10, "createdAt", "desc",
                        null, null, null, null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(transactionRepository);
    }

    // --------------------------------------------------
    // Low-stock alert tests
    // --------------------------------------------------

    private Product createLowStockProduct(
            Long id,
            String name,
            String sku,
            int availableQuantity,
            int threshold,
            String unit
    ) {
        Product lowStockProduct = mock(Product.class);

        when(lowStockProduct.getId()).thenReturn(id);
        when(lowStockProduct.getName()).thenReturn(name);
        when(lowStockProduct.getSku()).thenReturn(sku);
        when(lowStockProduct.getAvailableQuantity())
                .thenReturn(availableQuantity);
        when(lowStockProduct.getLowStockThreshold()).thenReturn(threshold);
        when(lowStockProduct.getUnit()).thenReturn(unit);

        return lowStockProduct;
    }

    @Test
    void getLowStockProducts_shouldReturnPaginatedAlerts() {

        authenticateAdmin();

        Product lowStockProduct = createLowStockProduct(
                1L,
                "Safety Gloves",
                "TF-GLOVE-001",
                5,
                10,
                "pcs"
        );

        Product outOfStockProduct = createLowStockProduct(
                2L,
                "Industrial Helmet",
                "TF-HELMET-001",
                0,
                10,
                "pcs"
        );

        Page<Product> productPage = new PageImpl<>(
                List.of(lowStockProduct, outOfStockProduct),
                PageRequest.of(
                        0,
                        10,
                        Sort.by(Sort.Direction.ASC, "availableQuantity")
                ),
                2
        );

        when(productRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(productPage);

        Page<LowStockProductResponse> response =
                inventoryService.getLowStockProducts(
                        0,
                        10,
                        "availableQuantity",
                        "asc",
                        null,
                        ADMIN_EMAIL
                );

        assertNotNull(response);
        assertEquals(2, response.getTotalElements());
        assertEquals(1, response.getTotalPages());

        LowStockProductResponse lowStockResult =
                response.getContent().get(0);

        assertEquals(1L, lowStockResult.productId());
        assertEquals("Safety Gloves", lowStockResult.productName());
        assertEquals("TF-GLOVE-001", lowStockResult.sku());
        assertEquals(5, lowStockResult.availableQuantity());
        assertEquals(10, lowStockResult.lowStockThreshold());
        assertEquals("pcs", lowStockResult.unit());
        assertEquals("LOW_STOCK", lowStockResult.status());

        LowStockProductResponse outOfStockResult =
                response.getContent().get(1);

        assertEquals(2L, outOfStockResult.productId());
        assertEquals("Industrial Helmet", outOfStockResult.productName());
        assertEquals(0, outOfStockResult.availableQuantity());
        assertEquals("OUT_OF_STOCK", outOfStockResult.status());

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(productRepository).findAll(
                any(Specification.class),
                pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();

        assertEquals(0, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals(
                Sort.Direction.ASC,
                pageable.getSort()
                        .getOrderFor("availableQuantity")
                        .getDirection()
        );
    }

    @Test
    void getLowStockProducts_shouldApplyLowStockStatusFilter() {

        authenticateAdmin();

        when(productRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<LowStockProductResponse> response =
                inventoryService.getLowStockProducts(
                        0,
                        10,
                        "availableQuantity",
                        "asc",
                        "LOW_STOCK",
                        ADMIN_EMAIL
                );

        assertNotNull(response);
        assertTrue(response.isEmpty());

        verify(productRepository).findAll(
                any(Specification.class),
                any(Pageable.class)
        );
    }

    @Test
    void getLowStockProducts_shouldApplyOutOfStockStatusFilter() {

        authenticateAdmin();

        when(productRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<LowStockProductResponse> response =
                inventoryService.getLowStockProducts(
                        0,
                        10,
                        "availableQuantity",
                        "asc",
                        "OUT_OF_STOCK",
                        ADMIN_EMAIL
                );

        assertNotNull(response);
        assertTrue(response.isEmpty());

        verify(productRepository).findAll(
                any(Specification.class),
                any(Pageable.class)
        );
    }

    @Test
    void getLowStockProducts_shouldNormalizeStatus() {

        authenticateAdmin();

        when(productRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        assertDoesNotThrow(() ->
                inventoryService.getLowStockProducts(
                        0,
                        10,
                        "availableQuantity",
                        "asc",
                        "  low_stock  ",
                        ADMIN_EMAIL
                )
        );

        verify(productRepository).findAll(
                any(Specification.class),
                any(Pageable.class)
        );
    }

    @Test
    void getLowStockProducts_shouldRejectNegativePage() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getLowStockProducts(
                        -1, 10, "availableQuantity", "asc",
                        null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(productRepository);
    }

    @Test
    void getLowStockProducts_shouldRejectInvalidPageSize() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getLowStockProducts(
                        0, 0, "availableQuantity", "asc",
                        null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(productRepository);
    }

    @Test
    void getLowStockProducts_shouldRejectPageSizeAboveMaximum() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getLowStockProducts(
                        0, 101, "availableQuantity", "asc",
                        null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(productRepository);
    }

    @Test
    void getLowStockProducts_shouldRejectInvalidSortField() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getLowStockProducts(
                        0, 10, "password", "asc",
                        null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(productRepository);
    }

    @Test
    void getLowStockProducts_shouldRejectInvalidSortDirection() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getLowStockProducts(
                        0, 10, "availableQuantity", "random",
                        null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(productRepository);
    }

    @Test
    void getLowStockProducts_shouldRejectInvalidStatus() {

        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getLowStockProducts(
                        0, 10, "availableQuantity", "asc",
                        "CRITICAL", ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(productRepository);
    }

    @Test
    void getLowStockProducts_shouldRejectBuyer() {

        when(buyer.isEnabled()).thenReturn(true);
        when(buyer.getRole()).thenReturn(UserRole.BUYER);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(buyer));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getLowStockProducts(
                        0, 10, "availableQuantity", "asc",
                        null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(productRepository);
    }

    @Test
    void getLowStockProducts_shouldRejectSupplier() {

        when(supplier.isEnabled()).thenReturn(true);
        when(supplier.getRole()).thenReturn(UserRole.SUPPLIER);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(supplier));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getLowStockProducts(
                        0, 10, "availableQuantity", "asc",
                        null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(productRepository);
    }

    @Test
    void getLowStockProducts_shouldRejectDisabledAdmin() {

        when(admin.isEnabled()).thenReturn(false);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(admin));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getLowStockProducts(
                        0, 10, "availableQuantity", "asc",
                        null, ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(productRepository);
    }

    // --------------------------------------------------
    // Stock-in tests
    // --------------------------------------------------

    @Test
    void stockIn_shouldIncreaseQuantityAndRecordTransaction() {

        authenticateAdmin();

        when(product.getAvailableQuantity()).thenReturn(1950);

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        when(productRepository.save(product)).thenReturn(product);

        StockInRequest request = new StockInRequest(
                PRODUCT_ID,
                100,
                "New stock received from supplier"
        );

        Product response = inventoryService.stockIn(
                request,
                ADMIN_EMAIL
        );

        assertNotNull(response);

        verify(product).setAvailableQuantity(2050);
        verify(productRepository).save(product);

        ArgumentCaptor<InventoryTransaction> captor =
                ArgumentCaptor.forClass(InventoryTransaction.class);

        verify(transactionRepository).save(captor.capture());

        InventoryTransaction transaction = captor.getValue();

        assertEquals(product, transaction.getProduct());
        assertNull(transaction.getPurchaseOrder());
        assertEquals(admin, transaction.getPerformedBy());
        assertEquals(
                InventoryTransactionType.STOCK_IN,
                transaction.getTransactionType()
        );
        assertEquals(100, transaction.getQuantity());
        assertEquals(1950, transaction.getPreviousQuantity());
        assertEquals(2050, transaction.getNewQuantity());
        assertEquals(
                "New stock received from supplier",
                transaction.getReason()
        );
    }

    @Test
    void stockIn_shouldRejectBuyer() {

        when(buyer.isEnabled()).thenReturn(true);
        when(buyer.getRole()).thenReturn(UserRole.BUYER);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(buyer));

        StockInRequest request = new StockInRequest(
                PRODUCT_ID,
                100,
                "Stock received"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockIn(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockIn_shouldRejectSupplier() {

        when(supplier.isEnabled()).thenReturn(true);
        when(supplier.getRole()).thenReturn(UserRole.SUPPLIER);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(supplier));

        StockInRequest request = new StockInRequest(
                PRODUCT_ID,
                100,
                "Stock received"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockIn(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockIn_shouldRejectDisabledAdmin() {

        when(admin.isEnabled()).thenReturn(false);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(admin));

        StockInRequest request = new StockInRequest(
                PRODUCT_ID,
                100,
                "Stock received"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockIn(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockIn_shouldRejectZeroQuantity() {

        authenticateAdmin();

        StockInRequest request = new StockInRequest(
                PRODUCT_ID,
                0,
                "Stock received"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockIn(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockIn_shouldRejectNegativeQuantity() {

        authenticateAdmin();

        StockInRequest request = new StockInRequest(
                PRODUCT_ID,
                -10,
                "Stock received"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockIn(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockIn_shouldRejectNonexistentProduct() {

        authenticateAdmin();

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.empty());

        StockInRequest request = new StockInRequest(
                PRODUCT_ID,
                100,
                "Stock received"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockIn(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockIn_shouldRejectIntegerOverflow() {

        authenticateAdmin();

        when(product.getAvailableQuantity())
                .thenReturn(Integer.MAX_VALUE - 10);

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        StockInRequest request = new StockInRequest(
                PRODUCT_ID,
                100,
                "Stock received"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockIn(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(product, never()).setAvailableQuantity(anyInt());
        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(transactionRepository);
    }

    // --------------------------------------------------
    // Stock-out tests
    // --------------------------------------------------

    @Test
    void stockOut_shouldDecreaseQuantityAndRecordTransaction() {

        authenticateAdmin();

        when(product.getAvailableQuantity()).thenReturn(100);

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        when(productRepository.save(product)).thenReturn(product);

        StockOutRequest request = new StockOutRequest(
                PRODUCT_ID,
                25,
                "Damaged products removed from warehouse"
        );

        Product response = inventoryService.stockOut(
                request,
                ADMIN_EMAIL
        );

        assertNotNull(response);

        verify(product).setAvailableQuantity(75);
        verify(productRepository).save(product);

        ArgumentCaptor<InventoryTransaction> captor =
                ArgumentCaptor.forClass(InventoryTransaction.class);

        verify(transactionRepository).save(captor.capture());

        InventoryTransaction transaction = captor.getValue();

        assertEquals(product, transaction.getProduct());
        assertNull(transaction.getPurchaseOrder());
        assertEquals(admin, transaction.getPerformedBy());

        assertEquals(
                InventoryTransactionType.STOCK_OUT,
                transaction.getTransactionType()
        );

        assertEquals(25, transaction.getQuantity());
        assertEquals(100, transaction.getPreviousQuantity());
        assertEquals(75, transaction.getNewQuantity());

        assertEquals(
                "Damaged products removed from warehouse",
                transaction.getReason()
        );
    }

    @Test
    void stockOut_shouldAllowRemovingEntireAvailableQuantity() {

        authenticateAdmin();

        when(product.getAvailableQuantity()).thenReturn(25);

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        StockOutRequest request = new StockOutRequest(
                PRODUCT_ID,
                25,
                "Entire stock removed"
        );

        inventoryService.stockOut(request, ADMIN_EMAIL);

        verify(product).setAvailableQuantity(0);
        verify(productRepository).save(product);
        verify(transactionRepository).save(any(InventoryTransaction.class));
    }

    @Test
    void stockOut_shouldRejectInsufficientInventory() {

        authenticateAdmin();

        when(product.getAvailableQuantity()).thenReturn(20);

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        StockOutRequest request = new StockOutRequest(
                PRODUCT_ID,
                30,
                "Warehouse stock removal"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockOut(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(product, never()).setAvailableQuantity(anyInt());
        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockOut_shouldRejectZeroQuantity() {

        authenticateAdmin();

        StockOutRequest request = new StockOutRequest(
                PRODUCT_ID,
                0,
                "Stock removal"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockOut(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockOut_shouldRejectNegativeQuantity() {

        authenticateAdmin();

        StockOutRequest request = new StockOutRequest(
                PRODUCT_ID,
                -10,
                "Stock removal"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockOut(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockOut_shouldRejectNonexistentProduct() {

        authenticateAdmin();

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.empty());

        StockOutRequest request = new StockOutRequest(
                PRODUCT_ID,
                10,
                "Stock removal"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockOut(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockOut_shouldRejectBuyer() {

        when(buyer.isEnabled()).thenReturn(true);
        when(buyer.getRole()).thenReturn(UserRole.BUYER);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(buyer));

        StockOutRequest request = new StockOutRequest(
                PRODUCT_ID,
                10,
                "Stock removal"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockOut(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockOut_shouldRejectSupplier() {

        when(supplier.isEnabled()).thenReturn(true);
        when(supplier.getRole()).thenReturn(UserRole.SUPPLIER);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(supplier));

        StockOutRequest request = new StockOutRequest(
                PRODUCT_ID,
                10,
                "Stock removal"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockOut(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void stockOut_shouldRejectDisabledAdmin() {

        when(admin.isEnabled()).thenReturn(false);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(admin));

        StockOutRequest request = new StockOutRequest(
                PRODUCT_ID,
                10,
                "Stock removal"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.stockOut(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    // --------------------------------------------------
    // Stock adjustment tests
    // --------------------------------------------------

    @Test
    void adjustStock_shouldIncreaseQuantityAndRecordTransaction() {

        authenticateAdmin();

        when(product.getAvailableQuantity()).thenReturn(100);

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        when(productRepository.save(product)).thenReturn(product);

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                PRODUCT_ID,
                50,
                "Physical stock count found 50 additional units"
        );

        Product response = inventoryService.adjustStock(
                request,
                ADMIN_EMAIL
        );

        assertNotNull(response);

        verify(product).setAvailableQuantity(150);
        verify(productRepository).save(product);

        ArgumentCaptor<InventoryTransaction> captor =
                ArgumentCaptor.forClass(InventoryTransaction.class);

        verify(transactionRepository).save(captor.capture());

        InventoryTransaction transaction = captor.getValue();

        assertEquals(InventoryTransactionType.ADJUSTMENT,
                transaction.getTransactionType());
        assertEquals(50, transaction.getQuantity());
        assertEquals(100, transaction.getPreviousQuantity());
        assertEquals(150, transaction.getNewQuantity());
        assertEquals(
                "Physical stock count found 50 additional units",
                transaction.getReason()
        );
        assertNull(transaction.getPurchaseOrder());
        assertEquals(admin, transaction.getPerformedBy());
    }

    @Test
    void adjustStock_shouldDecreaseQuantityAndRecordTransaction() {

        authenticateAdmin();

        when(product.getAvailableQuantity()).thenReturn(100);

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        when(productRepository.save(product)).thenReturn(product);

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                PRODUCT_ID,
                -30,
                "30 units damaged during storage"
        );

        Product response = inventoryService.adjustStock(
                request,
                ADMIN_EMAIL
        );

        assertNotNull(response);

        verify(product).setAvailableQuantity(70);
        verify(productRepository).save(product);

        ArgumentCaptor<InventoryTransaction> captor =
                ArgumentCaptor.forClass(InventoryTransaction.class);

        verify(transactionRepository).save(captor.capture());

        InventoryTransaction transaction = captor.getValue();

        assertEquals(InventoryTransactionType.ADJUSTMENT,
                transaction.getTransactionType());
        assertEquals(30, transaction.getQuantity());
        assertEquals(100, transaction.getPreviousQuantity());
        assertEquals(70, transaction.getNewQuantity());
        assertEquals(
                "30 units damaged during storage",
                transaction.getReason()
        );
    }

    @Test
    void adjustStock_shouldRejectZeroQuantity() {

        authenticateAdmin();

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                PRODUCT_ID,
                0,
                "Stock correction"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.adjustStock(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void adjustStock_shouldRejectAdjustmentBelowZero() {

        authenticateAdmin();

        when(product.getAvailableQuantity()).thenReturn(20);

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                PRODUCT_ID,
                -30,
                "Damaged stock correction"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.adjustStock(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(product, never()).setAvailableQuantity(anyInt());
        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void adjustStock_shouldRejectIntegerOverflow() {

        authenticateAdmin();

        when(product.getAvailableQuantity())
                .thenReturn(Integer.MAX_VALUE - 10);

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                PRODUCT_ID,
                100,
                "Stock correction"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.adjustStock(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(product, never()).setAvailableQuantity(anyInt());
        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void adjustStock_shouldRejectBuyer() {

        when(buyer.isEnabled()).thenReturn(true);
        when(buyer.getRole()).thenReturn(UserRole.BUYER);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(buyer));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                PRODUCT_ID,
                50,
                "Stock correction"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.adjustStock(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void adjustStock_shouldRejectDisabledAdmin() {

        when(admin.isEnabled()).thenReturn(false);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(admin));

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                PRODUCT_ID,
                50,
                "Stock correction"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.adjustStock(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(productRepository);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void adjustStock_shouldRejectNonexistentProduct() {

        authenticateAdmin();

        when(productRepository.findByIdForUpdate(PRODUCT_ID))
                .thenReturn(Optional.empty());

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                PRODUCT_ID,
                50,
                "Stock correction"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.adjustStock(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(transactionRepository);
    }

    // --------------------------------------------------
// Inventory dashboard summary tests
// --------------------------------------------------

    @Test
    void getInventorySummary_shouldReturnAggregatedMetricsForAdmin() {

        authenticateAdmin();

        InventorySummaryProjection summary =
                mock(InventorySummaryProjection.class);

        when(summary.getTotalProducts()).thenReturn(150L);
        when(summary.getTotalInventoryUnits()).thenReturn(25000L);
        when(summary.getLowStockProducts()).thenReturn(12L);
        when(summary.getOutOfStockProducts()).thenReturn(3L);
        when(summary.getHealthyStockProducts()).thenReturn(135L);

        when(productRepository.getInventorySummary())
                .thenReturn(summary);

        InventorySummaryResponse response =
                inventoryService.getInventorySummary(ADMIN_EMAIL);

        assertEquals(150L, response.totalProducts());
        assertEquals(25000L, response.totalInventoryUnits());
        assertEquals(12L, response.lowStockProducts());
        assertEquals(3L, response.outOfStockProducts());
        assertEquals(135L, response.healthyStockProducts());

        verify(productRepository, times(1)).getInventorySummary();
    }

    @Test
    void getInventorySummary_shouldRejectBuyer() {

        when(buyer.isEnabled()).thenReturn(true);
        when(buyer.getRole()).thenReturn(UserRole.BUYER);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(buyer));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getInventorySummary(ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(productRepository);
    }

    @Test
    void getInventorySummary_shouldRejectDisabledAdmin() {

        when(admin.isEnabled()).thenReturn(false);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(admin));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getInventorySummary(ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(productRepository);
    }



    // --------------------------------------------------
    // Inventory movement analytics tests
    // --------------------------------------------------

    @Test
    void getInventoryMovementAnalytics_shouldCalculateNetMovementForAdmin() {

        authenticateAdmin();

        InventoryMovementAnalyticsProjection analytics =
                mock(InventoryMovementAnalyticsProjection.class);

        when(analytics.getTotalStockIn()).thenReturn(1500L);
        when(analytics.getTotalStockOut()).thenReturn(450L);
        when(analytics.getAdjustmentIncrease()).thenReturn(100L);
        when(analytics.getAdjustmentDecrease()).thenReturn(25L);
        when(analytics.getTotalTransactions()).thenReturn(85L);

        when(transactionRepository.getInventoryMovementAnalytics(null, null))
                .thenReturn(analytics);

        InventoryMovementAnalyticsResponse response =
                inventoryService.getInventoryMovementAnalytics(
                        null,
                        null,
                        ADMIN_EMAIL
                );

        assertEquals(1500L, response.totalStockIn());
        assertEquals(450L, response.totalStockOut());
        assertEquals(100L, response.adjustmentIncrease());
        assertEquals(25L, response.adjustmentDecrease());
        assertEquals(1125L, response.netInventoryMovement());
        assertEquals(85L, response.totalTransactions());

        verify(transactionRepository).getInventoryMovementAnalytics(null, null);
    }

    @Test
    void getInventoryMovementAnalytics_shouldPassDateFiltersToRepository() {

        authenticateAdmin();

        LocalDateTime startDate =
                LocalDateTime.of(2026, 11, 1, 0, 0);

        LocalDateTime endDate =
                LocalDateTime.of(2026, 11, 7, 23, 59);

        InventoryMovementAnalyticsProjection analytics =
                mock(InventoryMovementAnalyticsProjection.class);

        when(analytics.getTotalStockIn()).thenReturn(0L);
        when(analytics.getTotalStockOut()).thenReturn(0L);
        when(analytics.getAdjustmentIncrease()).thenReturn(0L);
        when(analytics.getAdjustmentDecrease()).thenReturn(0L);
        when(analytics.getTotalTransactions()).thenReturn(0L);

        when(transactionRepository.getInventoryMovementAnalytics(
                startDate,
                endDate
        )).thenReturn(analytics);

        InventoryMovementAnalyticsResponse response =
                inventoryService.getInventoryMovementAnalytics(
                        startDate,
                        endDate,
                        ADMIN_EMAIL
                );

        assertNotNull(response);
        assertEquals(0L, response.netInventoryMovement());
        assertEquals(0L, response.totalTransactions());

        verify(transactionRepository).getInventoryMovementAnalytics(
                startDate,
                endDate
        );
    }

    @Test
    void getInventoryMovementAnalytics_shouldRejectInvalidDateRange() {

        authenticateAdmin();

        LocalDateTime startDate =
                LocalDateTime.of(2026, 11, 7, 0, 0);

        LocalDateTime endDate =
                LocalDateTime.of(2026, 11, 1, 0, 0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getInventoryMovementAnalytics(
                        startDate,
                        endDate,
                        ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getInventoryMovementAnalytics_shouldRejectBuyer() {

        when(buyer.isEnabled()).thenReturn(true);
        when(buyer.getRole()).thenReturn(UserRole.BUYER);

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(buyer));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> inventoryService.getInventoryMovementAnalytics(
                        null,
                        null,
                        ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(transactionRepository);
    }
}
