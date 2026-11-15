package com.tradeflow.api.shipment;

import com.tradeflow.api.order.OrderStatus;
import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.order.PurchaseOrderRepository;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.quotation.Quotation;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    private static final String SUPPLIER_EMAIL =
            "supplier@tradeflow.test";

    private static final String BUYER_EMAIL =
            "buyer@tradeflow.test";

    private static final String ADMIN_EMAIL =
            "admin@tradeflow.test";

    private static final Long SHIPMENT_ID = 2L;
    private static final Long ORDER_ID = 10L;

    private static final Long BUYER_ORGANIZATION_ID = 1L;
    private static final Long SUPPLIER_ORGANIZATION_ID = 2L;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private MembershipAuthorizationService membershipAuthorizationService;

    @InjectMocks
    private ShipmentService shipmentService;

    private User supplier;
    private User buyer;
    private User admin;

    private PurchaseOrder purchaseOrder;
    private Shipment shipment;

    private ShipmentRequest shipmentRequest;

    @BeforeEach
    void setUp() {

        supplier = new User(
                "Test Supplier",
                SUPPLIER_EMAIL,
                "password-hash",
                UserRole.SUPPLIER
        );

        buyer = new User(
                "Test Buyer",
                BUYER_EMAIL,
                "password-hash",
                UserRole.BUYER
        );

        admin = new User(
                "Test Admin",
                ADMIN_EMAIL,
                "password-hash",
                UserRole.ADMIN
        );

        purchaseOrder = mock(PurchaseOrder.class);

        shipment = new Shipment(
                purchaseOrder,
                "Blue Dart",
                "TF-TEST-TRACK-001",
                "Pune, Maharashtra, India",
                LocalDate.now().plusDays(5)
        );

        shipmentRequest = new ShipmentRequest(
                ORDER_ID,
                " Blue Dart ",
                " TF-NEW-TRACK-001 ",
                " Pune, Maharashtra, India ",
                LocalDate.now().plusDays(5)
        );
    }

    // ---------------------------------------------------------
    // Common mock helpers
    // ---------------------------------------------------------

    private void mockAuthenticatedSupplier() {

        when(membershipAuthorizationService.getAuthenticatedUser(
                SUPPLIER_EMAIL
        )).thenReturn(supplier);
    }

    private void mockAuthenticatedBuyer() {

        when(membershipAuthorizationService.getAuthenticatedUser(
                BUYER_EMAIL
        )).thenReturn(buyer);
    }

    private void mockShipmentLookup() {

        when(shipmentRepository.findById(SHIPMENT_ID))
                .thenReturn(Optional.of(shipment));
    }

    private void mockPurchaseOrderLookup() {

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(purchaseOrder));
    }

    /**
     * Configures:
     * PurchaseOrder -> Quotation -> Supplier -> Organization.
     */
    private void mockSupplierOrganization() {

        Organization supplierOrganization = mock(Organization.class);

        when(supplierOrganization.getId())
                .thenReturn(SUPPLIER_ORGANIZATION_ID);

        Quotation quotation = mock(Quotation.class);

        Supplier supplierProfile = mock(Supplier.class);

        when(purchaseOrder.getQuotation())
                .thenReturn(quotation);

        when(quotation.getSupplier())
                .thenReturn(supplierProfile);

        when(supplierProfile.getOrganization())
                .thenReturn(supplierOrganization);
    }

    /**
     * Configures the complete buyer and supplier organization chain.
     */
    private void mockOrderOrganizations() {

        Organization buyerOrganization = mock(Organization.class);
        Organization supplierOrganization = mock(Organization.class);

        when(buyerOrganization.getId())
                .thenReturn(BUYER_ORGANIZATION_ID);

        when(supplierOrganization.getId())
                .thenReturn(SUPPLIER_ORGANIZATION_ID);

        ProcurementRequest procurementRequest =
                mock(ProcurementRequest.class);

        Quotation quotation = mock(Quotation.class);

        Supplier supplierProfile = mock(Supplier.class);

        when(purchaseOrder.getQuotation())
                .thenReturn(quotation);

        when(quotation.getProcurementRequest())
                .thenReturn(procurementRequest);

        when(procurementRequest.getBuyerOrganization())
                .thenReturn(buyerOrganization);

        when(quotation.getSupplier())
                .thenReturn(supplierProfile);

        when(supplierProfile.getOrganization())
                .thenReturn(supplierOrganization);
    }

    private void mockSupplierCapability() {

        mockSupplierOrganization();

        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenReturn(null);
    }

    private void mockBuyerCapability() {

        when(membershipAuthorizationService.requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenReturn(null);
    }

    private void mockShipmentReadByBuyer() {

        mockOrderOrganizations();

        when(membershipAuthorizationService.requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenReturn(null);
    }

    private void mockShipmentReadBySupplier() {

        mockOrderOrganizations();

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Buyer capability unavailable"
        )).when(membershipAuthorizationService)
                .requireOrganizationCapability(
                        SUPPLIER_EMAIL,
                        BUYER_ORGANIZATION_ID,
                        OrganizationCapability.BUYER
                );

        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenReturn(null);
    }

    private void mockUnauthorizedShipmentRead() {

        mockOrderOrganizations();

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Buyer capability unavailable"
        )).when(membershipAuthorizationService)
                .requireOrganizationCapability(
                        SUPPLIER_EMAIL,
                        BUYER_ORGANIZATION_ID,
                        OrganizationCapability.BUYER
                );

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Supplier capability unavailable"
        )).when(membershipAuthorizationService)
                .requireOrganizationCapability(
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID,
                        OrganizationCapability.SUPPLIER
                );
    }

    // ---------------------------------------------------------
    // Shipment creation tests
    // ---------------------------------------------------------

    @Test
    void createShipment_shouldAllowAuthorizedSupplier() {

        mockAuthenticatedSupplier();
        mockPurchaseOrderLookup();
        mockSupplierCapability();

        when(purchaseOrder.getId()).thenReturn(ORDER_ID);
        when(purchaseOrder.getStatus()).thenReturn(OrderStatus.SHIPPED);

        when(shipmentRepository.existsByPurchaseOrder_Id(ORDER_ID))
                .thenReturn(false);

        when(shipmentRepository.existsByTrackingNumber("TF-NEW-TRACK-001"))
                .thenReturn(false);

        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Shipment result = shipmentService.createShipment(
                shipmentRequest,
                SUPPLIER_EMAIL
        );

        assertEquals("Blue Dart", result.getCarrierName());

        assertEquals(
                "TF-NEW-TRACK-001",
                result.getTrackingNumber()
        );

        assertEquals(
                "Pune, Maharashtra, India",
                result.getDeliveryAddress()
        );

        assertEquals(
                ShipmentStatus.CREATED,
                result.getStatus()
        );

        verify(shipmentRepository).save(any(Shipment.class));
    }

    @Test
    void createShipment_shouldRejectPlatformAdmin() {

        when(membershipAuthorizationService.getAuthenticatedUser(
                ADMIN_EMAIL
        )).thenReturn(admin);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.createShipment(
                        shipmentRequest,
                        ADMIN_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        assertEquals(
                "Platform administrators cannot create shipments",
                exception.getReason()
        );

        verify(purchaseOrderRepository, never()).findById(any());
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void createShipment_shouldRejectSupplierWithoutCapability() {

        mockAuthenticatedSupplier();
        mockPurchaseOrderLookup();
        mockSupplierOrganization();

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Supplier capability unavailable"
        )).when(membershipAuthorizationService)
                .requireOrganizationCapability(
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID,
                        OrganizationCapability.SUPPLIER
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.createShipment(
                        shipmentRequest,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void createShipment_shouldRejectOrderNotShipped() {

        mockAuthenticatedSupplier();
        mockPurchaseOrderLookup();
        mockSupplierCapability();

        when(purchaseOrder.getStatus()).thenReturn(OrderStatus.CONFIRMED);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.createShipment(
                        shipmentRequest,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(shipmentRepository, never()).save(any());
    }

    // ---------------------------------------------------------
    // Read shipment by ID
    // ---------------------------------------------------------

    @Test
    void getShipmentById_shouldAllowBuyerOrganization() {

        mockAuthenticatedBuyer();
        mockShipmentLookup();
        mockShipmentReadByBuyer();

        Shipment result = shipmentService.getShipmentById(
                SHIPMENT_ID,
                BUYER_EMAIL
        );

        assertSame(shipment, result);

        verify(membershipAuthorizationService)
                .requireOrganizationCapability(
                        BUYER_EMAIL,
                        BUYER_ORGANIZATION_ID,
                        OrganizationCapability.BUYER
                );
    }

    @Test
    void getShipmentById_shouldAllowSupplierOrganization() {

        mockAuthenticatedSupplier();
        mockShipmentLookup();
        mockShipmentReadBySupplier();

        Shipment result = shipmentService.getShipmentById(
                SHIPMENT_ID,
                SUPPLIER_EMAIL
        );

        assertSame(shipment, result);

        verify(membershipAuthorizationService)
                .requireOrganizationCapability(
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID,
                        OrganizationCapability.SUPPLIER
                );
    }

    @Test
    void getShipmentById_shouldRejectUnrelatedOrganization() {

        mockAuthenticatedSupplier();
        mockShipmentLookup();
        mockUnauthorizedShipmentRead();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.getShipmentById(
                        SHIPMENT_ID,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void getShipmentById_shouldAllowPlatformAdmin() {

        when(membershipAuthorizationService.getAuthenticatedUser(
                ADMIN_EMAIL
        )).thenReturn(admin);

        mockShipmentLookup();

        Shipment result = shipmentService.getShipmentById(
                SHIPMENT_ID,
                ADMIN_EMAIL
        );

        assertSame(shipment, result);

        verify(membershipAuthorizationService, never())
                .requireOrganizationCapability(
                        any(),
                        any(),
                        any()
                );
    }

    // ---------------------------------------------------------
    // Read shipment by tracking number
    // ---------------------------------------------------------

    @Test
    void getShipmentByTrackingNumber_shouldAllowAuthorizedBuyer() {

        mockAuthenticatedBuyer();
        mockShipmentReadByBuyer();

        when(shipmentRepository.findByTrackingNumber(
                "TF-TEST-TRACK-001"
        )).thenReturn(Optional.of(shipment));

        Shipment result = shipmentService.getShipmentByTrackingNumber(
                " TF-TEST-TRACK-001 ",
                BUYER_EMAIL
        );

        assertSame(shipment, result);
    }

    @Test
    void getShipmentByTrackingNumber_shouldRejectUnauthorizedSupplier() {

        mockAuthenticatedSupplier();
        mockUnauthorizedShipmentRead();

        when(shipmentRepository.findByTrackingNumber(
                "TF-TEST-TRACK-001"
        )).thenReturn(Optional.of(shipment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.getShipmentByTrackingNumber(
                        "TF-TEST-TRACK-001",
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    // ---------------------------------------------------------
    // Read shipment by purchase order ID
    // ---------------------------------------------------------

    @Test
    void getShipmentByOrderId_shouldAllowAuthorizedBuyer() {

        mockAuthenticatedBuyer();
        mockPurchaseOrderLookup();
        mockShipmentReadByBuyer();

        when(shipmentRepository.findByPurchaseOrder_Id(ORDER_ID))
                .thenReturn(Optional.of(shipment));

        Shipment result = shipmentService.getShipmentByOrderId(
                ORDER_ID,
                BUYER_EMAIL
        );

        assertSame(shipment, result);
    }

    @Test
    void getShipmentByOrderId_shouldRejectUnauthorizedSupplier() {

        mockAuthenticatedSupplier();
        mockPurchaseOrderLookup();
        mockUnauthorizedShipmentRead();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.getShipmentByOrderId(
                        ORDER_ID,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(shipmentRepository, never())
                .findByPurchaseOrder_Id(any());
    }

    // ---------------------------------------------------------
    // Platform ADMIN listing tests
    // ---------------------------------------------------------

    @Test
    void getAllShipments_shouldAllowPlatformAdmin() {

        List<Shipment> shipments = List.of(shipment);

        when(shipmentRepository.findAll()).thenReturn(shipments);

        when(membershipAuthorizationService.requirePlatformAdmin(
                ADMIN_EMAIL
        )).thenReturn(admin);

        List<Shipment> result = shipmentService.getAllShipments(
                ADMIN_EMAIL
        );

        assertEquals(1, result.size());
        assertSame(shipment, result.get(0));

        verify(membershipAuthorizationService)
                .requirePlatformAdmin(ADMIN_EMAIL);
    }

    @Test
    void getShipmentsByStatus_shouldAllowPlatformAdmin() {

        List<Shipment> shipments = List.of(shipment);

        when(shipmentRepository.findByStatus(ShipmentStatus.CREATED))
                .thenReturn(shipments);

        when(membershipAuthorizationService.requirePlatformAdmin(
                ADMIN_EMAIL
        )).thenReturn(admin);

        List<Shipment> result = shipmentService.getShipmentsByStatus(
                ShipmentStatus.CREATED,
                ADMIN_EMAIL
        );

        assertEquals(1, result.size());

        verify(membershipAuthorizationService)
                .requirePlatformAdmin(ADMIN_EMAIL);
    }

    @Test
    void getAllShipments_shouldRejectNonAdmin() {

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Platform administrator access required"
        )).when(membershipAuthorizationService)
                .requirePlatformAdmin(SUPPLIER_EMAIL);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.getAllShipments(SUPPLIER_EMAIL)
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(shipmentRepository, never()).findAll();
    }

    // ---------------------------------------------------------
    // Shipment status update tests
    // ---------------------------------------------------------

    @Test
    void updateShipmentStatus_shouldAllowValidTransition() {

        mockAuthenticatedSupplier();
        mockShipmentLookup();
        mockSupplierCapability();

        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Shipment result = shipmentService.updateShipmentStatus(
                SHIPMENT_ID,
                ShipmentStatus.PICKED_UP,
                SUPPLIER_EMAIL
        );

        assertEquals(ShipmentStatus.PICKED_UP, result.getStatus());

        verify(shipmentRepository).save(shipment);
    }

    @Test
    void updateShipmentStatus_shouldRejectInvalidTransition() {

        mockAuthenticatedSupplier();
        mockShipmentLookup();
        mockSupplierCapability();

        shipment.setStatus(ShipmentStatus.DELIVERED);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.updateShipmentStatus(
                        SHIPMENT_ID,
                        ShipmentStatus.IN_TRANSIT,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void updateShipmentStatus_shouldMarkShipmentDeliveredWithoutDeliveringOrder() {

        mockAuthenticatedSupplier();
        mockShipmentLookup();
        mockSupplierCapability();

        shipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);

        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Shipment result = shipmentService.updateShipmentStatus(
                SHIPMENT_ID,
                ShipmentStatus.DELIVERED,
                SUPPLIER_EMAIL
        );

        assertEquals(ShipmentStatus.DELIVERED, result.getStatus());
        assertEquals(LocalDate.now(), result.getActualDeliveryDate());

        verify(purchaseOrder, never()).deliver();
        verify(shipmentRepository).save(shipment);
    }

    @Test
    void updateShipmentStatus_shouldRejectDisabledUser() {

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Your account is disabled"
        )).when(membershipAuthorizationService)
                .getAuthenticatedUser(SUPPLIER_EMAIL);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.updateShipmentStatus(
                        SHIPMENT_ID,
                        ShipmentStatus.OUT_FOR_DELIVERY,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void updateShipmentStatus_shouldRejectNullStatus() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.updateShipmentStatus(
                        SHIPMENT_ID,
                        null,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(400, exception.getStatusCode().value());

        verify(membershipAuthorizationService, never())
                .getAuthenticatedUser(any());

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void updateShipmentStatus_shouldRejectSupplierWithoutCapability() {

        mockAuthenticatedSupplier();
        mockShipmentLookup();
        mockSupplierOrganization();

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Supplier capability unavailable"
        )).when(membershipAuthorizationService)
                .requireOrganizationCapability(
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID,
                        OrganizationCapability.SUPPLIER
                );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.updateShipmentStatus(
                        SHIPMENT_ID,
                        ShipmentStatus.PICKED_UP,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void updateShipmentStatus_shouldRejectPlatformAdmin() {

        when(membershipAuthorizationService.getAuthenticatedUser(
                ADMIN_EMAIL
        )).thenReturn(admin);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.updateShipmentStatus(
                        SHIPMENT_ID,
                        ShipmentStatus.PICKED_UP,
                        ADMIN_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(shipmentRepository, never()).findById(any());
        verify(shipmentRepository, never()).save(any());
    }
    @Test
    void getShipmentsByStatus_shouldRejectNonAdmin() {

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Platform administrator access required"
        )).when(membershipAuthorizationService)
                .requirePlatformAdmin(SUPPLIER_EMAIL);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.getShipmentsByStatus(
                        ShipmentStatus.CREATED,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(shipmentRepository, never())
                .findByStatus(any());
    }
}
