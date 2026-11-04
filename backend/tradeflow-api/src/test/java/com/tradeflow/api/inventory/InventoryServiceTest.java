package com.tradeflow.api.inventory;

import com.tradeflow.api.product.Product;
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
}