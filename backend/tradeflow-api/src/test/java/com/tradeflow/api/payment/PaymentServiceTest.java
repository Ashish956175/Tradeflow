package com.tradeflow.api.payment;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String BUYER_EMAIL = "buyer@tradeflow.test";
    private static final String ADMIN_EMAIL = "admin@tradeflow.test";
    private static final String SUPPLIER_EMAIL = "supplier@tradeflow.test";

    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 10L;

    private static final Long BUYER_ORGANIZATION_ID = 100L;
    private static final Long SUPPLIER_ORGANIZATION_ID = 200L;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private MembershipAuthorizationService membershipAuthorizationService;

    @InjectMocks
    private PaymentService paymentService;

    private User buyer;
    private User admin;
    private User supplierUser;

    private PurchaseOrder order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        buyer = mock(User.class);
        admin = mock(User.class);
        supplierUser = mock(User.class);

        order = mock(PurchaseOrder.class);
        payment = mock(Payment.class);
    }

    // -------------------------------------------------------------------------
    // AUTHENTICATION HELPERS
    // -------------------------------------------------------------------------

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

    private void authenticateAdminUser() {
        when(membershipAuthorizationService.getAuthenticatedUser(ADMIN_EMAIL))
                .thenReturn(admin);

        when(admin.getRole()).thenReturn(UserRole.ADMIN);
    }

    private void authenticatePlatformAdmin() {
        when(membershipAuthorizationService.requirePlatformAdmin(ADMIN_EMAIL))
                .thenReturn(admin);
    }

    // -------------------------------------------------------------------------
    // ORGANIZATION AUTHORIZATION HELPERS
    // -------------------------------------------------------------------------

    private void authorizeBuyerOrganization() {
        when(membershipAuthorizationService.requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenReturn(null);
    }

    private void denyBuyerOrganization() {
        when(membershipAuthorizationService.requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have the required organization capability"
        ));
    }

    // Configure only the buyer organization.
    // Used by createPayment(), which only needs buyer organization access.
    private void configureBuyerOrderOrganization() {

        Quotation quotation = mock(Quotation.class);
        ProcurementRequest procurementRequest = mock(ProcurementRequest.class);
        Organization buyerOrganization = mock(Organization.class);

        when(buyerOrganization.getId()).thenReturn(BUYER_ORGANIZATION_ID);

        when(quotation.getProcurementRequest()).thenReturn(procurementRequest);
        when(procurementRequest.getBuyerOrganization())
                .thenReturn(buyerOrganization);

        when(order.getQuotation()).thenReturn(quotation);
    }

    // Configure both organizations.
    // Used by payment retrieval authorization checks.
    private void configureOrderOrganizations() {

        Quotation quotation = mock(Quotation.class);
        ProcurementRequest procurementRequest = mock(ProcurementRequest.class);

        Organization buyerOrganization = mock(Organization.class);
        when(buyerOrganization.getId()).thenReturn(BUYER_ORGANIZATION_ID);

        Supplier supplierEntity = mock(Supplier.class);
        Organization supplierOrganization = mock(Organization.class);

        when(supplierOrganization.getId()).thenReturn(SUPPLIER_ORGANIZATION_ID);

        when(quotation.getProcurementRequest()).thenReturn(procurementRequest);
        when(procurementRequest.getBuyerOrganization())
                .thenReturn(buyerOrganization);

        when(quotation.getSupplier()).thenReturn(supplierEntity);
        when(supplierEntity.getOrganization()).thenReturn(supplierOrganization);

        when(order.getQuotation()).thenReturn(quotation);
    }

    // Configure fields required when a PaymentResponse is constructed.
    // Do not use this helper in tests that expect authorization to fail.
    private void configurePaymentMapping() {
        when(payment.getId()).thenReturn(PAYMENT_ID);
        when(payment.getPurchaseOrder()).thenReturn(order);
        when(order.getId()).thenReturn(ORDER_ID);
    }

    // -------------------------------------------------------------------------
    // CREATE PAYMENT
    // -------------------------------------------------------------------------

    @Test
    void createPayment_shouldRejectUnauthenticatedUser() {

        when(membershipAuthorizationService.getAuthenticatedUser(BUYER_EMAIL))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        PaymentRequest request = new PaymentRequest(ORDER_ID, "UPI");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(request, BUYER_EMAIL)
        );

        assertEquals(401, exception.getStatusCode().value());

        verifyNoInteractions(purchaseOrderRepository);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void createPayment_shouldRejectNonBuyer() {

        authenticateAdminUser();

        PaymentRequest request = new PaymentRequest(ORDER_ID, "UPI");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(request, ADMIN_EMAIL)
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(purchaseOrderRepository);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void createPayment_shouldRejectMissingOrder() {

        authenticateBuyer();

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.empty());

        PaymentRequest request = new PaymentRequest(ORDER_ID, "UPI");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(request, BUYER_EMAIL)
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPayment_shouldRejectUnconfirmedOrder() {

        authenticateBuyer();
        configureBuyerOrderOrganization();
        authorizeBuyerOrganization();

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getStatus()).thenReturn(OrderStatus.CREATED);

        PaymentRequest request = new PaymentRequest(ORDER_ID, "UPI");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(request, BUYER_EMAIL)
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPayment_shouldRejectDisabledBuyer() {

        when(membershipAuthorizationService.getAuthenticatedUser(BUYER_EMAIL))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Your account is disabled"
                ));

        PaymentRequest request = new PaymentRequest(ORDER_ID, "UPI");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(request, BUYER_EMAIL)
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(purchaseOrderRepository);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void createPayment_shouldRejectPaymentForAnotherBuyerOrganization() {

        authenticateBuyer();
        configureBuyerOrderOrganization();
        denyBuyerOrganization();

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        PaymentRequest request = new PaymentRequest(ORDER_ID, "UPI");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(request, BUYER_EMAIL)
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPayment_shouldRejectDuplicatePayment() {

        authenticateBuyer();
        configureBuyerOrderOrganization();
        authorizeBuyerOrganization();

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(order.getId()).thenReturn(ORDER_ID);

        when(paymentRepository.existsByPurchaseOrder_Id(ORDER_ID))
                .thenReturn(true);

        PaymentRequest request = new PaymentRequest(ORDER_ID, "UPI");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(request, BUYER_EMAIL)
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPayment_shouldSuccessfullyCreatePayment() {

        authenticateBuyer();
        configureBuyerOrderOrganization();
        authorizeBuyerOrganization();

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(order.getId()).thenReturn(ORDER_ID);
        when(order.getTotalAmount()).thenReturn(new BigDecimal("5000.00"));

        when(paymentRepository.existsByPurchaseOrder_Id(ORDER_ID))
                .thenReturn(false);

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentRequest request = new PaymentRequest(ORDER_ID, " upi ");

        PaymentResponse result = paymentService.createPayment(
                request,
                BUYER_EMAIL
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("5000.00"), result.amount());
        assertEquals(PaymentStatus.PENDING, result.status());
        assertEquals("UPI", result.paymentMethod());

        verify(paymentRepository).save(argThat(savedPayment ->
                savedPayment.getAmount().compareTo(
                        new BigDecimal("5000.00")
                ) == 0
                        && savedPayment.getStatus() == PaymentStatus.PENDING
                        && "UPI".equals(savedPayment.getPaymentMethod())
        ));
    }

    // -------------------------------------------------------------------------
    // COMPLETE PAYMENT
    // -------------------------------------------------------------------------

    @Test
    void markPaymentAsCompleted_shouldRejectNonAdmin() {

        when(membershipAuthorizationService.requirePlatformAdmin(BUYER_EMAIL))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Platform administrator permission is required"
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.markPaymentAsCompleted(
                        PAYMENT_ID,
                        BUYER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void markPaymentAsCompleted_shouldRejectNonPendingPayment() {

        authenticatePlatformAdmin();

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(payment.getStatus()).thenReturn(PaymentStatus.COMPLETED);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.markPaymentAsCompleted(
                        PAYMENT_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void markPaymentAsCompleted_shouldRejectMissingPayment() {

        authenticatePlatformAdmin();

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.markPaymentAsCompleted(
                        PAYMENT_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void markPaymentAsCompleted_shouldSuccessfullyCompletePendingPayment() {

        authenticatePlatformAdmin();
        configurePaymentMapping();

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);

        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentResponse result = paymentService.markPaymentAsCompleted(
                PAYMENT_ID,
                ADMIN_EMAIL
        );

        assertNotNull(result);

        verify(payment).setStatus(PaymentStatus.COMPLETED);
        verify(paymentRepository).save(payment);
    }

    // -------------------------------------------------------------------------
    // FAIL PAYMENT
    // -------------------------------------------------------------------------

    @Test
    void markPaymentAsFailed_shouldRejectNonPendingPayment() {

        authenticatePlatformAdmin();

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(payment.getStatus()).thenReturn(PaymentStatus.FAILED);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.markPaymentAsFailed(
                        PAYMENT_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void markPaymentAsFailed_shouldRejectCompletedPayment() {

        authenticatePlatformAdmin();

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(payment.getStatus()).thenReturn(PaymentStatus.COMPLETED);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.markPaymentAsFailed(
                        PAYMENT_ID,
                        ADMIN_EMAIL
                )
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void markPaymentAsFailed_shouldSuccessfullyFailPendingPayment() {

        authenticatePlatformAdmin();
        configurePaymentMapping();

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);

        when(paymentRepository.save(payment)).thenReturn(payment);

        PaymentResponse result = paymentService.markPaymentAsFailed(
                PAYMENT_ID,
                ADMIN_EMAIL
        );

        assertNotNull(result);

        verify(payment).setStatus(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
    }

    // -------------------------------------------------------------------------
    // GET PAYMENT BY ID
    // -------------------------------------------------------------------------

    @Test
    void getPaymentById_shouldAllowSupplierFromOwningOrganization() {

        authenticateSupplier();
        configurePaymentMapping();
        configureOrderOrganizations();

        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Buyer capability denied"
        ));

        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenReturn(null);

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        PaymentResponse result = paymentService.getPaymentById(
                PAYMENT_ID,
                SUPPLIER_EMAIL
        );

        assertNotNull(result);
        assertEquals(PAYMENT_ID, result.id());
        assertEquals(ORDER_ID, result.purchaseOrderId());

        verify(paymentRepository).findById(PAYMENT_ID);
    }

    @Test
    void getPaymentById_shouldRejectSupplierFromAnotherOrganization() {

        authenticateSupplier();

        // Only the purchase order is needed for authorization.
        when(payment.getPurchaseOrder()).thenReturn(order);
        configureOrderOrganizations();

        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Buyer capability denied"
        ));

        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Supplier capability denied"
        ));

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.getPaymentById(
                        PAYMENT_ID,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void getPaymentById_shouldAllowBuyerFromOwningOrganization() {

        authenticateBuyer();
        configurePaymentMapping();
        configureOrderOrganizations();
        authorizeBuyerOrganization();

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        PaymentResponse result = paymentService.getPaymentById(
                PAYMENT_ID,
                BUYER_EMAIL
        );

        assertNotNull(result);
        assertEquals(PAYMENT_ID, result.id());
        assertEquals(ORDER_ID, result.purchaseOrderId());

        verify(paymentRepository).findById(PAYMENT_ID);
    }

    @Test
    void getPaymentById_shouldRejectBuyerFromAnotherOrganization() {

        authenticateBuyer();

        // Only the purchase order is needed for authorization.
        when(payment.getPurchaseOrder()).thenReturn(order);
        configureOrderOrganizations();

        denyBuyerOrganization();

        when(membershipAuthorizationService.requireOrganizationCapability(
                BUYER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Supplier capability denied"
        ));

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.getPaymentById(
                        PAYMENT_ID,
                        BUYER_EMAIL
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void getPaymentById_shouldAllowAdminAccess() {

        authenticateAdminUser();
        configurePaymentMapping();

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        PaymentResponse result = paymentService.getPaymentById(
                PAYMENT_ID,
                ADMIN_EMAIL
        );

        assertNotNull(result);
        assertEquals(PAYMENT_ID, result.id());
        assertEquals(ORDER_ID, result.purchaseOrderId());

        verify(paymentRepository).findById(PAYMENT_ID);
    }

    // -------------------------------------------------------------------------
    // GET PAYMENT BY ORDER ID
    // -------------------------------------------------------------------------

    @Test
    void getPaymentByOrderId_shouldAllowBuyerFromOwningOrganization() {

        authenticateBuyer();
        configureOrderOrganizations();
        authorizeBuyerOrganization();
        configurePaymentMapping();

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByPurchaseOrder_Id(ORDER_ID))
                .thenReturn(Optional.of(payment));

        PaymentResponse result = paymentService.getPaymentByOrderId(
                ORDER_ID,
                BUYER_EMAIL
        );

        assertNotNull(result);
        assertEquals(PAYMENT_ID, result.id());
        assertEquals(ORDER_ID, result.purchaseOrderId());

        verify(purchaseOrderRepository).findById(ORDER_ID);
        verify(paymentRepository).findByPurchaseOrder_Id(ORDER_ID);
    }

    @Test
    void getPaymentByOrderId_shouldAllowSupplierFromOwningOrganization() {

        authenticateSupplier();
        configureOrderOrganizations();
        configurePaymentMapping();

        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Buyer capability denied"
        ));

        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenReturn(null);

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(paymentRepository.findByPurchaseOrder_Id(ORDER_ID))
                .thenReturn(Optional.of(payment));

        PaymentResponse result = paymentService.getPaymentByOrderId(
                ORDER_ID,
                SUPPLIER_EMAIL
        );

        assertNotNull(result);
        assertEquals(PAYMENT_ID, result.id());
        assertEquals(ORDER_ID, result.purchaseOrderId());

        verify(purchaseOrderRepository).findById(ORDER_ID);
        verify(paymentRepository).findByPurchaseOrder_Id(ORDER_ID);
    }

    @Test
    void getPaymentByOrderId_shouldRejectSupplierFromAnotherOrganization() {

        authenticateSupplier();
        configureOrderOrganizations();

        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Buyer capability denied"
        ));

        when(membershipAuthorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Supplier capability denied"
        ));

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.getPaymentByOrderId(
                        ORDER_ID,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(purchaseOrderRepository).findById(ORDER_ID);
        verify(paymentRepository, never())
                .findByPurchaseOrder_Id(ORDER_ID);
    }

    // -------------------------------------------------------------------------
    // GET PAYMENTS BY STATUS
    // -------------------------------------------------------------------------

    @Test
    void getPaymentsByStatus_shouldAllowAdminAccess() {

        authenticatePlatformAdmin();

        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);
        configurePaymentMapping();

        List<Payment> payments = List.of(payment);

        when(paymentRepository.findByStatus(PaymentStatus.PENDING))
                .thenReturn(payments);

        List<PaymentResponse> result = paymentService.getPaymentsByStatus(
                PaymentStatus.PENDING,
                ADMIN_EMAIL
        );

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(PAYMENT_ID, result.get(0).id());
        assertEquals(ORDER_ID, result.get(0).purchaseOrderId());
        assertEquals(PaymentStatus.PENDING, result.get(0).status());

        verify(paymentRepository).findByStatus(PaymentStatus.PENDING);
    }

    @Test
    void getPaymentsByStatus_shouldRejectBuyerAccess() {

        when(membershipAuthorizationService.requirePlatformAdmin(BUYER_EMAIL))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Platform administrator permission is required"
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.getPaymentsByStatus(
                        PaymentStatus.PENDING,
                        BUYER_EMAIL
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(paymentRepository, never())
                .findByStatus(PaymentStatus.PENDING);
    }

    // -------------------------------------------------------------------------
    // GET ALL PAYMENTS
    // -------------------------------------------------------------------------

    @Test
    void getAllPayments_shouldAllowAdminAccess() {

        authenticatePlatformAdmin();
        configurePaymentMapping();

        List<Payment> payments = List.of(payment);

        when(paymentRepository.findAll()).thenReturn(payments);

        List<PaymentResponse> result = paymentService.getAllPayments(ADMIN_EMAIL);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(PAYMENT_ID, result.get(0).id());
        assertEquals(ORDER_ID, result.get(0).purchaseOrderId());

        verify(paymentRepository).findAll();
    }

    @Test
    void getAllPayments_shouldRejectBuyerAccess() {

        when(membershipAuthorizationService.requirePlatformAdmin(BUYER_EMAIL))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Platform administrator permission is required"
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.getAllPayments(BUYER_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(paymentRepository, never()).findAll();
    }
}