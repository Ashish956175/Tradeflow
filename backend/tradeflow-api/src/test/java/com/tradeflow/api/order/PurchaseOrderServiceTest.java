package com.tradeflow.api.order;

import com.tradeflow.api.inventory.InventoryService;
import com.tradeflow.api.product.Product;
import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.quotation.Quotation;
import com.tradeflow.api.quotation.QuotationRepository;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private static final String ADMIN_EMAIL = "admin@tradeflow.test";
    private static final String BUYER_EMAIL = "buyer@tradeflow.test";

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    private User admin;
    private User buyer;
    private PurchaseOrder order;
    private Quotation quotation;
    private ProcurementRequest procurementRequest;
    private Product product;

    @BeforeEach
    void setUp() {
        admin = mock(User.class);
        buyer = mock(User.class);
        order = mock(PurchaseOrder.class);
        quotation = mock(Quotation.class);
        procurementRequest = mock(ProcurementRequest.class);
        product = mock(Product.class);
    }

    // --------------------------------------------------
    // Authentication helpers
    // --------------------------------------------------

    private void authenticateAdmin() {
        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(admin));

        when(admin.isEnabled()).thenReturn(true);
        when(admin.getRole()).thenReturn(UserRole.ADMIN);
    }

    private void authenticateBuyer() {
        when(userRepository.findByEmail(BUYER_EMAIL))
                .thenReturn(Optional.of(buyer));

        when(buyer.isEnabled()).thenReturn(true);
        when(buyer.getRole()).thenReturn(UserRole.BUYER);
    }

    // --------------------------------------------------
    // Purchase order setup helper
    // --------------------------------------------------

    private void configureOrder(OrderStatus status, int quantity) {
        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getStatus()).thenReturn(status);
        when(order.getOrderedQuantity()).thenReturn(quantity);
    }

    private void configureProductRelationship() {
        when(order.getQuotation()).thenReturn(quotation);

        when(quotation.getProcurementRequest())
                .thenReturn(procurementRequest);

        when(procurementRequest.getProduct())
                .thenReturn(product);

        when(product.getId()).thenReturn(PRODUCT_ID);
    }

    // --------------------------------------------------
    // Confirm order tests
    // --------------------------------------------------

    @Test
    void confirmOrder_shouldReserveInventoryAndConfirmOrder() {
        authenticateAdmin();
        configureOrder(OrderStatus.CREATED, 5);
        configureProductRelationship();

        when(purchaseOrderRepository.save(order))
                .thenReturn(order);

        PurchaseOrder result = purchaseOrderService.confirmOrder(
                ORDER_ID,
                ADMIN_EMAIL
        );

        assertSame(order, result);

        verify(inventoryService).reserveForOrder(
                PRODUCT_ID,
                5,
                admin,
                order
        );

        verify(order).confirm();

        verify(purchaseOrderRepository).save(order);

        verify(inventoryService, never()).releaseForOrder(
                anyLong(),
                anyInt(),
                any(User.class),
                any(PurchaseOrder.class)
        );
    }

    @Test
    void confirmOrder_shouldRejectInsufficientInventory() {
        authenticateAdmin();
        configureOrder(OrderStatus.CREATED, 5);
        configureProductRelationship();

        doThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Insufficient inventory for this order"
        )).when(inventoryService).reserveForOrder(
                PRODUCT_ID,
                5,
                admin,
                order
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.confirmOrder(
                        ORDER_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(inventoryService).reserveForOrder(
                PRODUCT_ID,
                5,
                admin,
                order
        );

        verify(order, never()).confirm();

        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void confirmOrder_shouldRejectAlreadyConfirmedOrder() {
        authenticateAdmin();

        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.confirmOrder(
                        ORDER_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(inventoryService, never()).reserveForOrder(
                anyLong(),
                anyInt(),
                any(User.class),
                any(PurchaseOrder.class)
        );

        verify(order, never()).confirm();

        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void confirmOrder_shouldRejectZeroQuantity() {
        authenticateAdmin();
        configureOrder(OrderStatus.CREATED, 0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.confirmOrder(
                        ORDER_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(inventoryService, never()).reserveForOrder(
                anyLong(),
                anyInt(),
                any(User.class),
                any(PurchaseOrder.class)
        );

        verify(order, never()).confirm();

        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void confirmOrder_shouldRejectNegativeQuantity() {
        authenticateAdmin();
        configureOrder(OrderStatus.CREATED, -5);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.confirmOrder(
                        ORDER_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(inventoryService, never()).reserveForOrder(
                anyLong(),
                anyInt(),
                any(User.class),
                any(PurchaseOrder.class)
        );

        verify(order, never()).confirm();

        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void confirmOrder_shouldRejectNonAdmin() {
        authenticateBuyer();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.confirmOrder(
                        ORDER_ID,
                        BUYER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(inventoryService);

        verify(purchaseOrderRepository, never())
                .findByIdForUpdate(anyLong());
    }

    // --------------------------------------------------
    // Ship order tests
    // --------------------------------------------------

    @Test
    void shipOrder_shouldRecordShipmentAndPreserveAvailableInventory() {
        authenticateAdmin();
        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getOrderedQuantity()).thenReturn(5);
        configureProductRelationship();

        when(purchaseOrderRepository.save(order))
                .thenReturn(order);

        PurchaseOrder result = purchaseOrderService.shipOrder(
                ORDER_ID,
                ADMIN_EMAIL
        );

        assertSame(order, result);

        verify(order).ship();

        verify(inventoryService).recordShipmentForOrder(
                PRODUCT_ID,
                5,
                admin,
                order
        );

        verify(purchaseOrderRepository).save(order);
    }

    @Test
    void shipOrder_shouldRejectInvalidOrderStatus() {
        authenticateAdmin();
        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        doThrow(new IllegalStateException("Order must be confirmed before shipping"))
                .when(order).ship();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.shipOrder(
                        ORDER_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verifyNoInteractions(inventoryService);

        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void shipOrder_shouldRejectNonAdmin() {
        authenticateBuyer();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.shipOrder(
                        ORDER_ID,
                        BUYER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(inventoryService);

        verify(purchaseOrderRepository, never())
                .findByIdForUpdate(anyLong());
    }

    @Test
    void shipOrder_shouldNotSaveOrderWhenInventoryRecordingFails() {
        authenticateAdmin();
        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getOrderedQuantity()).thenReturn(5);
        configureProductRelationship();

        doThrow(new RuntimeException("Inventory recording failed"))
                .when(inventoryService)
                .recordShipmentForOrder(
                        PRODUCT_ID,
                        5,
                        admin,
                        order
                );

        assertThrows(
                RuntimeException.class,
                () -> purchaseOrderService.shipOrder(
                        ORDER_ID,
                        ADMIN_EMAIL
                )
        );

        verify(order).ship();

        verify(inventoryService).recordShipmentForOrder(
                PRODUCT_ID,
                5,
                admin,
                order
        );

        verify(purchaseOrderRepository, never()).save(any());
    }

    // --------------------------------------------------
    // Cancel order tests
    // --------------------------------------------------

    @Test
    void cancelOrder_shouldReleaseInventoryForConfirmedOrder() {
        authenticateAdmin();
        configureOrder(OrderStatus.CONFIRMED, 5);
        configureProductRelationship();

        when(purchaseOrderRepository.save(order))
                .thenReturn(order);

        PurchaseOrder result = purchaseOrderService.cancelOrder(
                ORDER_ID,
                ADMIN_EMAIL
        );

        assertSame(order, result);

        verify(order).cancel();

        verify(inventoryService).releaseForOrder(
                PRODUCT_ID,
                5,
                admin,
                order
        );

        verify(purchaseOrderRepository).save(order);
    }

    @Test
    void cancelOrder_shouldNotReleaseInventoryForCreatedOrder() {
        authenticateAdmin();

        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getStatus()).thenReturn(OrderStatus.CREATED);

        when(purchaseOrderRepository.save(order))
                .thenReturn(order);

        PurchaseOrder result = purchaseOrderService.cancelOrder(
                ORDER_ID,
                ADMIN_EMAIL
        );

        assertSame(order, result);

        verify(order).cancel();

        verify(inventoryService, never()).releaseForOrder(
                anyLong(),
                anyInt(),
                any(User.class),
                any(PurchaseOrder.class)
        );

        verify(purchaseOrderRepository).save(order);
    }

    @Test
    void cancelOrder_shouldRejectShippedOrder() {
        authenticateAdmin();

        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getStatus()).thenReturn(OrderStatus.SHIPPED);

        doThrow(new IllegalStateException(
                "Only created or confirmed orders can be cancelled"
        )).when(order).cancel();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.cancelOrder(
                        ORDER_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(order).cancel();

        verify(inventoryService, never()).releaseForOrder(
                anyLong(),
                anyInt(),
                any(User.class),
                any(PurchaseOrder.class)
        );

        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_shouldRejectNonAdmin() {
        authenticateBuyer();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.cancelOrder(
                        ORDER_ID,
                        BUYER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(inventoryService);

        verify(purchaseOrderRepository, never())
                .findByIdForUpdate(anyLong());
    }

    @Test
    void cancelOrder_shouldRejectWhenInventoryReleaseFails() {
        authenticateAdmin();
        configureOrder(OrderStatus.CONFIRMED, 5);
        configureProductRelationship();

        doThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Inventory release failed"
        )).when(inventoryService).releaseForOrder(
                PRODUCT_ID,
                5,
                admin,
                order
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.cancelOrder(
                        ORDER_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(order).cancel();

        verify(inventoryService).releaseForOrder(
                PRODUCT_ID,
                5,
                admin,
                order
        );

        verify(purchaseOrderRepository, never()).save(any());
    }
}