package com.tradeflow.api.order;

import com.tradeflow.api.inventory.InventoryService;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.product.Product;
import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.quotation.Quotation;
import com.tradeflow.api.quotation.QuotationRepository;
import com.tradeflow.api.security.MembershipAuthorizationService;
import com.tradeflow.api.supplier.Supplier;
import com.tradeflow.api.user.User;
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
    private static final Long BUYER_ORG_ID = 100L;
    private static final Long SUPPLIER_ORG_ID = 200L;

    private static final String ADMIN_EMAIL = "admin@tradeflow.test";
    private static final String BUYER_EMAIL = "buyer@tradeflow.test";
    private static final String SUPPLIER_EMAIL = "supplier@tradeflow.test";

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private MembershipAuthorizationService membershipAuthorizationService;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    private User admin;
    private User buyer;
    private User supplierUser;

    private PurchaseOrder order;
    private Quotation quotation;
    private ProcurementRequest procurementRequest;
    private Product product;

    private Organization buyerOrganization;
    private Organization supplierOrganization;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        admin = mock(User.class);
        buyer = mock(User.class);
        supplierUser = mock(User.class);

        order = mock(PurchaseOrder.class);
        quotation = mock(Quotation.class);
        procurementRequest = mock(ProcurementRequest.class);
        product = mock(Product.class);

        buyerOrganization = mock(Organization.class);
        supplierOrganization = mock(Organization.class);
        supplier = mock(Supplier.class);
    }

    // --------------------------------------------------
    // Authentication helpers
    // --------------------------------------------------

    private void authenticateAdmin() {
        when(membershipAuthorizationService.getAuthenticatedUser(ADMIN_EMAIL))
                .thenReturn(admin);

        when(admin.getRole()).thenReturn(UserRole.ADMIN);
    }

    private void authenticateBuyer() {
        when(membershipAuthorizationService.getAuthenticatedUser(BUYER_EMAIL))
                .thenReturn(buyer);

        when(buyer.getRole()).thenReturn(UserRole.BUYER);
    }

    private void authenticateSupplier() {
        when(membershipAuthorizationService.getAuthenticatedUser(SUPPLIER_EMAIL))
                .thenReturn(supplierUser);

        when(supplierUser.getRole()).thenReturn(UserRole.SUPPLIER);
    }

    // --------------------------------------------------
    // Organization authorization helpers
    // --------------------------------------------------

    private void configureBuyerAuthorization() {
        when(order.getQuotation()).thenReturn(quotation);

        when(quotation.getProcurementRequest())
                .thenReturn(procurementRequest);

        when(procurementRequest.getBuyerOrganization())
                .thenReturn(buyerOrganization);

        when(buyerOrganization.getId())
                .thenReturn(BUYER_ORG_ID);
    }

    private void configureSupplierAuthorization() {
        when(order.getQuotation()).thenReturn(quotation);

        when(quotation.getSupplier()).thenReturn(supplier);

        when(supplier.getOrganization())
                .thenReturn(supplierOrganization);

        when(supplierOrganization.getId())
                .thenReturn(SUPPLIER_ORG_ID);
    }

    private void authorizeBuyer() {
        when(membershipAuthorizationService.requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORG_ID,
                OrganizationCapability.BUYER
        )).thenReturn(null);
    }

    private void authorizeSupplier() {
        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORG_ID,
                OrganizationCapability.SUPPLIER
        )).thenReturn(null);
    }

    // --------------------------------------------------
    // Purchase order setup helpers
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
        authenticateSupplier();
        configureOrder(OrderStatus.CREATED, 5);
        configureProductRelationship();
        configureSupplierAuthorization();
        authorizeSupplier();

        when(purchaseOrderRepository.save(order))
                .thenReturn(order);

        PurchaseOrder result = purchaseOrderService.confirmOrder(
                ORDER_ID,
                SUPPLIER_EMAIL
        );

        assertSame(order, result);

        verify(inventoryService).reserveForOrder(
                PRODUCT_ID,
                5,
                supplierUser,
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
        authenticateSupplier();
        configureOrder(OrderStatus.CREATED, 5);
        configureProductRelationship();
        configureSupplierAuthorization();
        authorizeSupplier();

        doThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Insufficient inventory for this order"
        )).when(inventoryService).reserveForOrder(
                PRODUCT_ID,
                5,
                supplierUser,
                order
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.confirmOrder(
                        ORDER_ID,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(inventoryService).reserveForOrder(
                PRODUCT_ID,
                5,
                supplierUser,
                order
        );

        verify(order, never()).confirm();
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void confirmOrder_shouldRejectAlreadyConfirmedOrder() {
        authenticateSupplier();
        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getStatus())
                .thenReturn(OrderStatus.CONFIRMED);
        configureSupplierAuthorization();
        authorizeSupplier();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.confirmOrder(
                        ORDER_ID,
                        SUPPLIER_EMAIL
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
        authenticateSupplier();
        configureOrder(OrderStatus.CREATED, 0);
        configureSupplierAuthorization();
        authorizeSupplier();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.confirmOrder(
                        ORDER_ID,
                        SUPPLIER_EMAIL
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
        authenticateSupplier();
        configureOrder(OrderStatus.CREATED, -5);
        configureSupplierAuthorization();
        authorizeSupplier();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.confirmOrder(
                        ORDER_ID,
                        SUPPLIER_EMAIL
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
    void confirmOrder_shouldRejectBuyerWithoutSupplierCapability() {
        authenticateBuyer();
        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));
        configureSupplierAuthorization();

        when(membershipAuthorizationService.requireOrganizationCapability(
                BUYER_EMAIL,
                SUPPLIER_ORG_ID,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Supplier capability required"
        ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.confirmOrder(
                        ORDER_ID,
                        BUYER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(inventoryService);
        verify(order, never()).confirm();
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void confirmOrder_shouldRejectPlatformAdmin() {
        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.confirmOrder(
                        ORDER_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(purchaseOrderRepository, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(inventoryService);
    }

    // --------------------------------------------------
    // Ship order tests
    // --------------------------------------------------

    @Test
    void shipOrder_shouldRecordShipmentAndPreserveAvailableInventory() {
        authenticateSupplier();

        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getOrderedQuantity()).thenReturn(5);

        configureProductRelationship();
        configureSupplierAuthorization();
        authorizeSupplier();

        when(purchaseOrderRepository.save(order))
                .thenReturn(order);

        PurchaseOrder result = purchaseOrderService.shipOrder(
                ORDER_ID,
                SUPPLIER_EMAIL
        );

        assertSame(order, result);

        verify(order).ship();

        verify(inventoryService).recordShipmentForOrder(
                PRODUCT_ID,
                5,
                supplierUser,
                order
        );

        verify(purchaseOrderRepository).save(order);
    }

    @Test
    void shipOrder_shouldRejectInvalidOrderStatus() {
        authenticateSupplier();

        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        configureSupplierAuthorization();
        authorizeSupplier();

        doThrow(new IllegalStateException(
                "Order must be confirmed before shipping"
        )).when(order).ship();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.shipOrder(
                        ORDER_ID,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verifyNoInteractions(inventoryService);
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void shipOrder_shouldRejectBuyerWithoutSupplierCapability() {
        authenticateBuyer();

        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        configureSupplierAuthorization();

        when(membershipAuthorizationService.requireOrganizationCapability(
                BUYER_EMAIL,
                SUPPLIER_ORG_ID,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Supplier capability required"
        ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.shipOrder(
                        ORDER_ID,
                        BUYER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(inventoryService);
        verify(order, never()).ship();
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void shipOrder_shouldRejectPlatformAdmin() {
        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.shipOrder(
                        ORDER_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(purchaseOrderRepository, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void shipOrder_shouldNotSaveOrderWhenInventoryRecordingFails() {
        authenticateSupplier();

        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getOrderedQuantity()).thenReturn(5);

        configureProductRelationship();
        configureSupplierAuthorization();
        authorizeSupplier();

        doThrow(new RuntimeException("Inventory recording failed"))
                .when(inventoryService)
                .recordShipmentForOrder(
                        PRODUCT_ID,
                        5,
                        supplierUser,
                        order
                );

        assertThrows(
                RuntimeException.class,
                () -> purchaseOrderService.shipOrder(
                        ORDER_ID,
                        SUPPLIER_EMAIL
                )
        );

        verify(order).ship();

        verify(inventoryService).recordShipmentForOrder(
                PRODUCT_ID,
                5,
                supplierUser,
                order
        );

        verify(purchaseOrderRepository, never()).save(any());
    }

    // --------------------------------------------------
    // Cancel order tests
    // --------------------------------------------------

    @Test
    void cancelOrder_shouldReleaseInventoryForConfirmedOrder() {
        authenticateBuyer();

        configureOrder(OrderStatus.CONFIRMED, 5);
        configureProductRelationship();
        configureBuyerAuthorization();
        authorizeBuyer();

        when(purchaseOrderRepository.save(order))
                .thenReturn(order);

        PurchaseOrder result = purchaseOrderService.cancelOrder(
                ORDER_ID,
                BUYER_EMAIL
        );

        assertSame(order, result);

        verify(order).cancel();

        verify(inventoryService).releaseForOrder(
                PRODUCT_ID,
                5,
                buyer,
                order
        );

        verify(purchaseOrderRepository).save(order);
    }

    @Test
    void cancelOrder_shouldNotReleaseInventoryForCreatedOrder() {
        authenticateBuyer();

        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getStatus()).thenReturn(OrderStatus.CREATED);

        configureBuyerAuthorization();
        authorizeBuyer();

        when(purchaseOrderRepository.save(order))
                .thenReturn(order);

        PurchaseOrder result = purchaseOrderService.cancelOrder(
                ORDER_ID,
                BUYER_EMAIL
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
        authenticateBuyer();

        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getStatus()).thenReturn(OrderStatus.SHIPPED);

        configureBuyerAuthorization();
        authorizeBuyer();

        doThrow(new IllegalStateException(
                "Only created or confirmed orders can be cancelled"
        )).when(order).cancel();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.cancelOrder(
                        ORDER_ID,
                        BUYER_EMAIL
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
    void cancelOrder_shouldRejectSupplierWithoutBuyerCapability() {
        authenticateSupplier();

        when(purchaseOrderRepository.findByIdForUpdate(ORDER_ID))
                .thenReturn(Optional.of(order));

        configureBuyerAuthorization();

        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                BUYER_ORG_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Buyer capability required"
        ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.cancelOrder(
                        ORDER_ID,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(inventoryService);
        verify(order, never()).cancel();
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_shouldRejectPlatformAdmin() {
        authenticateAdmin();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.cancelOrder(
                        ORDER_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(purchaseOrderRepository, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void cancelOrder_shouldRejectWhenInventoryReleaseFails() {
        authenticateBuyer();

        configureOrder(OrderStatus.CONFIRMED, 5);
        configureProductRelationship();
        configureBuyerAuthorization();
        authorizeBuyer();

        doThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Inventory release failed"
        )).when(inventoryService).releaseForOrder(
                PRODUCT_ID,
                5,
                buyer,
                order
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> purchaseOrderService.cancelOrder(
                        ORDER_ID,
                        BUYER_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(order).cancel();

        verify(inventoryService).releaseForOrder(
                PRODUCT_ID,
                5,
                buyer,
                order
        );

        verify(purchaseOrderRepository, never()).save(any());
    }
}