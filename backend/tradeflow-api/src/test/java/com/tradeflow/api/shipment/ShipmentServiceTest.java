package com.tradeflow.api.shipment;

import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.order.PurchaseOrderRepository;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.quotation.Quotation;

import com.tradeflow.api.supplier.Supplier;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    private static final String SUPPLIER_EMAIL =
            "supplier@tradeflow.test";

    private static final Long SHIPMENT_ID = 2L;
    private static final Long SUPPLIER_ORGANIZATION_ID = 2L;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ShipmentService shipmentService;

    private User supplier;
    private PurchaseOrder purchaseOrder;
    private Shipment shipment;

    @BeforeEach
    void setUp() {
        Organization organization = mock(Organization.class);

        lenient()
                .when(organization.getId())
                .thenReturn(SUPPLIER_ORGANIZATION_ID);

        supplier = new User(
                "Test Supplier",
                SUPPLIER_EMAIL,
                "password-hash",
                UserRole.SUPPLIER
        );

        supplier.setOrganization(organization);

        purchaseOrder = mock(PurchaseOrder.class);

        shipment = new Shipment(
                purchaseOrder,
                "Blue Dart",
                "TF-TEST-TRACK-001",
                "Pune, Maharashtra, India",
                LocalDate.now().plusDays(5)
        );
    }

    private void mockAuthenticatedSupplier() {
        lenient()
                .when(userRepository.findByEmail(SUPPLIER_EMAIL))
                .thenReturn(Optional.of(supplier));
    }

    private void mockShipmentLookup() {
        lenient()
                .when(shipmentRepository.findById(SHIPMENT_ID))
                .thenReturn(Optional.of(shipment));
    }

    private void mockSupplierOwnership() {
        Quotation quotation = mock(Quotation.class);
        Supplier supplierProfile = mock(Supplier.class);

        when(purchaseOrder.getQuotation())
                .thenReturn(quotation);

        when(quotation.getSupplier())
                .thenReturn(supplierProfile);

        when(supplierProfile.getOrganization())
                .thenReturn(supplier.getOrganization());
    }

    @Test
    void updateShipmentStatus_shouldAllowValidTransition() {
        mockAuthenticatedSupplier();
        mockShipmentLookup();
        mockSupplierOwnership();

        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Shipment result = shipmentService.updateShipmentStatus(
                SHIPMENT_ID,
                ShipmentStatus.PICKED_UP,
                SUPPLIER_EMAIL
        );

        assertEquals(
                ShipmentStatus.PICKED_UP,
                result.getStatus()
        );

        verify(shipmentRepository).save(shipment);
    }

    @Test
    void updateShipmentStatus_shouldRejectInvalidTransition() {
        mockAuthenticatedSupplier();
        mockShipmentLookup();
        mockSupplierOwnership();

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

        assertEquals(
                "Invalid shipment status transition from DELIVERED to IN_TRANSIT",
                exception.getReason()
        );

        verify(
                shipmentRepository,
                never()
        ).save(any(Shipment.class));
    }

    @Test
    void updateShipmentStatus_shouldSynchronizeOrderWhenDelivered() {
        mockAuthenticatedSupplier();
        mockShipmentLookup();
        mockSupplierOwnership();

        shipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);

        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Shipment result = shipmentService.updateShipmentStatus(
                SHIPMENT_ID,
                ShipmentStatus.DELIVERED,
                SUPPLIER_EMAIL
        );

        assertEquals(
                ShipmentStatus.DELIVERED,
                result.getStatus()
        );

        assertEquals(
                LocalDate.now(),
                result.getActualDeliveryDate()
        );

        verify(purchaseOrder).deliver();
        verify(shipmentRepository).save(shipment);
    }

    @Test
    void updateShipmentStatus_shouldRejectDisabledUser() {
        mockAuthenticatedSupplier();

        supplier.setEnabled(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.updateShipmentStatus(
                        SHIPMENT_ID,
                        ShipmentStatus.OUT_FOR_DELIVERY,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        assertEquals(
                "Your account is disabled",
                exception.getReason()
        );

        verify(
                shipmentRepository,
                never()
        ).save(any(Shipment.class));
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

        assertEquals(
                "Shipment status cannot be null",
                exception.getReason()
        );

        verify(
                shipmentRepository,
                never()
        ).save(any(Shipment.class));
    }
}
