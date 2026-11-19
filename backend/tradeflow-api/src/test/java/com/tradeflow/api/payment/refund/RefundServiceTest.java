package com.tradeflow.api.payment.refund;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.payment.Payment;
import com.tradeflow.api.payment.PaymentRepository;
import com.tradeflow.api.payment.PaymentStatus;
import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.quotation.Quotation;
import com.tradeflow.api.security.MembershipAuthorizationService;
import com.tradeflow.api.supplier.Supplier;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRole;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Long REFUND_ID = 1L;
    private static final Long PAYMENT_ID = 10L;

    private static final Long BUYER_ORGANIZATION_ID = 100L;
    private static final Long SUPPLIER_ORGANIZATION_ID = 200L;

    private static final String BUYER_EMAIL = "buyer@test.com";
    private static final String SUPPLIER_EMAIL = "supplier@test.com";
    private static final String ADMIN_EMAIL = "admin@test.com";

    private static final BigDecimal PAYMENT_AMOUNT =
            new BigDecimal("1000.00");

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MembershipAuthorizationService authorizationService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private User buyer;

    @Mock
    private User supplierUser;

    @Mock
    private User admin;

    @Mock
    private Organization buyerOrganization;

    @Mock
    private Organization supplierOrganization;

    @Mock
    private Payment payment;

    @Mock
    private PurchaseOrder purchaseOrder;

    @Mock
    private Quotation quotation;

    @Mock
    private ProcurementRequest procurementRequest;

    @Mock
    private Supplier supplier;

    @InjectMocks
    private RefundService refundService;

    // --------------------------------------------------
    // Authentication helpers
    // --------------------------------------------------

    private void authenticateBuyer() {
        when(authorizationService.getAuthenticatedUser(BUYER_EMAIL))
                .thenReturn(buyer);

        when(buyer.getRole()).thenReturn(UserRole.BUYER);
    }

    private void authenticateSupplier() {
        when(authorizationService.getAuthenticatedUser(SUPPLIER_EMAIL))
                .thenReturn(supplierUser);

        when(supplierUser.getRole()).thenReturn(UserRole.SUPPLIER);
    }

    private void authenticateAdmin() {
        when(authorizationService.getAuthenticatedUser(ADMIN_EMAIL))
                .thenReturn(admin);

        when(admin.getRole()).thenReturn(UserRole.ADMIN);
    }

    // --------------------------------------------------
    // Payment ownership helpers
    // --------------------------------------------------

    private void mockBuyerPaymentOwnership() {
        when(payment.getPurchaseOrder()).thenReturn(purchaseOrder);

        when(purchaseOrder.getQuotation()).thenReturn(quotation);

        when(quotation.getProcurementRequest())
                .thenReturn(procurementRequest);

        when(procurementRequest.getBuyerOrganization())
                .thenReturn(buyerOrganization);

        when(buyerOrganization.getId())
                .thenReturn(BUYER_ORGANIZATION_ID);
    }

    private void mockSupplierPaymentOwnership() {
        when(payment.getPurchaseOrder()).thenReturn(purchaseOrder);

        when(purchaseOrder.getQuotation()).thenReturn(quotation);

        when(quotation.getSupplier()).thenReturn(supplier);

        when(supplier.getOrganization()).thenReturn(supplierOrganization);

        when(supplierOrganization.getId())
                .thenReturn(SUPPLIER_ORGANIZATION_ID);
    }

    private void mockCompletePaymentOwnership() {
        mockBuyerPaymentOwnership();
        mockSupplierPaymentOwnership();
    }

    private void mockPaymentLookup() {
        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));
    }

    // --------------------------------------------------
    // Refund request tests
    // --------------------------------------------------

    @Test
    void requestRefund_shouldCreatePartialRefund() {

        authenticateBuyer();
        mockBuyerPaymentOwnership();

        when(payment.getId()).thenReturn(PAYMENT_ID);
        when(payment.getAmount()).thenReturn(PAYMENT_AMOUNT);
        when(payment.getStatus()).thenReturn(PaymentStatus.COMPLETED);

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.sumRefundAmountsByPaymentAndStatuses(
                eq(PAYMENT_ID), anyList()))
                .thenReturn(BigDecimal.ZERO);

        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefundRequest request = new RefundRequest(
                PAYMENT_ID,
                new BigDecimal("300.00"),
                "Damaged product"
        );

        RefundResponse response =
                refundService.requestRefund(request, BUYER_EMAIL);

        assertNotNull(response);
        assertEquals(PAYMENT_ID, response.paymentId());
        assertEquals(new BigDecimal("300.00"), response.amount());
        assertEquals("Damaged product", response.reason());
        assertEquals(RefundStatus.REQUESTED, response.status());

        verify(authorizationService).requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );

        verify(refundRepository).save(any(Refund.class));
    }

    @Test
    void requestRefund_shouldCreateFullRefund() {

        authenticateBuyer();
        mockBuyerPaymentOwnership();

        when(payment.getId()).thenReturn(PAYMENT_ID);
        when(payment.getAmount()).thenReturn(PAYMENT_AMOUNT);
        when(payment.getStatus()).thenReturn(PaymentStatus.COMPLETED);

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.sumRefundAmountsByPaymentAndStatuses(
                eq(PAYMENT_ID), anyList()))
                .thenReturn(BigDecimal.ZERO);

        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefundRequest request = new RefundRequest(
                PAYMENT_ID,
                PAYMENT_AMOUNT,
                "Order cancelled"
        );

        RefundResponse response =
                refundService.requestRefund(request, BUYER_EMAIL);

        assertNotNull(response);
        assertEquals(PAYMENT_ID, response.paymentId());
        assertEquals(PAYMENT_AMOUNT, response.amount());
        assertEquals(RefundStatus.REQUESTED, response.status());
    }

    @Test
    void requestRefund_shouldRejectAmountExceedingBalance() {

        authenticateBuyer();
        mockBuyerPaymentOwnership();

        when(payment.getId()).thenReturn(PAYMENT_ID);
        when(payment.getAmount()).thenReturn(PAYMENT_AMOUNT);
        when(payment.getStatus()).thenReturn(PaymentStatus.COMPLETED);

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.sumRefundAmountsByPaymentAndStatuses(
                eq(PAYMENT_ID), anyList()))
                .thenReturn(new BigDecimal("700.00"));

        RefundRequest request = new RefundRequest(
                PAYMENT_ID,
                new BigDecimal("400.00"),
                "Refund request"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.requestRefund(request, BUYER_EMAIL)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void requestRefund_shouldRejectIncompletePayment() {

        authenticateBuyer();
        mockBuyerPaymentOwnership();

        when(payment.getStatus()).thenReturn(PaymentStatus.PENDING);

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        RefundRequest request = new RefundRequest(
                PAYMENT_ID,
                new BigDecimal("500.00"),
                "Refund request"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.requestRefund(request, BUYER_EMAIL)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void requestRefund_shouldRejectAdmin() {

        authenticateAdmin();

        RefundRequest request = new RefundRequest(
                PAYMENT_ID,
                new BigDecimal("500.00"),
                "Refund request"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.requestRefund(request, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(paymentRepository, never()).findByIdForUpdate(anyLong());
        verify(authorizationService, never())
                .requireOrganizationCapability(
                        anyString(),
                        anyLong(),
                        any(OrganizationCapability.class)
                );
    }

    @Test
    void requestRefund_shouldRejectBuyerWithoutBuyerCapability() {

        authenticateBuyer();

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        mockBuyerPaymentOwnership();

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "BUYER capability is not enabled"
        )).when(authorizationService).requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );

        RefundRequest request = new RefundRequest(
                PAYMENT_ID,
                new BigDecimal("500.00"),
                "Refund request"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.requestRefund(request, BUYER_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void requestRefund_shouldRejectBuyerWithWrongOrganizationMembership() {

        authenticateBuyer();

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        mockBuyerPaymentOwnership();

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "No active membership"
        )).when(authorizationService).requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );

        RefundRequest request = new RefundRequest(
                PAYMENT_ID,
                new BigDecimal("500.00"),
                "Refund request"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.requestRefund(request, BUYER_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void requestRefund_shouldRejectDisabledBuyer() {

        when(authorizationService.getAuthenticatedUser(BUYER_EMAIL))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User account is disabled"
                ));

        RefundRequest request = new RefundRequest(
                PAYMENT_ID,
                new BigDecimal("500.00"),
                "Refund request"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.requestRefund(request, BUYER_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(paymentRepository, never()).findByIdForUpdate(anyLong());
    }

    // --------------------------------------------------
    // Refund read authorization tests
    // --------------------------------------------------

    @Test
    void getRefundById_shouldAllowBuyerWithBuyerCapability() {

        authenticateBuyer();
        mockCompletePaymentOwnership();

        Refund refund = new Refund(
                payment,
                new BigDecimal("300.00"),
                "Damaged product"
        );

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        RefundResponse response =
                refundService.getRefundById(REFUND_ID, BUYER_EMAIL);

        assertNotNull(response);

        verify(authorizationService).requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );
    }

    @Test
    void getRefundById_shouldAllowSupplierWithSupplierCapability() {

        authenticateSupplier();
        mockCompletePaymentOwnership();

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "No BUYER membership"
        )).when(authorizationService).requireOrganizationCapability(
                SUPPLIER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );

        Refund refund = new Refund(
                payment,
                new BigDecimal("300.00"),
                "Damaged product"
        );

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        RefundResponse response =
                refundService.getRefundById(REFUND_ID, SUPPLIER_EMAIL);

        assertNotNull(response);

        verify(authorizationService).requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        );
    }

    @Test
    void getRefundById_shouldRejectSupplierWithoutSupplierCapability() {

        authenticateSupplier();
        mockCompletePaymentOwnership();

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "No BUYER membership"
        )).when(authorizationService).requireOrganizationCapability(
                SUPPLIER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "No SUPPLIER membership"
        )).when(authorizationService).requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        );

        Refund refund = new Refund(
                payment,
                new BigDecimal("300.00"),
                "Damaged product"
        );

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.getRefundById(REFUND_ID, SUPPLIER_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void getRefundById_shouldNotFallbackWhenBuyerCheckReturnsUnauthorized() {

        authenticateSupplier();
        mockCompletePaymentOwnership();

        doThrow(new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication required"
        )).when(authorizationService).requireOrganizationCapability(
                SUPPLIER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );

        Refund refund = new Refund(
                payment,
                new BigDecimal("300.00"),
                "Damaged product"
        );

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.getRefundById(REFUND_ID, SUPPLIER_EMAIL)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        verify(authorizationService, never()).requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        );
    }

    @Test
    void getRefundById_shouldAllowPlatformAdmin() {

        authenticateAdmin();

        Refund refund = new Refund(
                payment,
                new BigDecimal("300.00"),
                "Damaged product"
        );

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        RefundResponse response =
                refundService.getRefundById(REFUND_ID, ADMIN_EMAIL);

        assertNotNull(response);

        verify(authorizationService, never())
                .requireOrganizationCapability(
                        anyString(),
                        anyLong(),
                        any(OrganizationCapability.class)
                );
    }

    @Test
    void getRefundsByPaymentId_shouldAllowBuyerWithBuyerCapability() {

        authenticateBuyer();
        mockCompletePaymentOwnership();
        mockPaymentLookup();

        when(refundRepository.findByPayment_IdOrderByCreatedAtDesc(PAYMENT_ID))
                .thenReturn(List.of());

        List<RefundResponse> responses =
                refundService.getRefundsByPaymentId(PAYMENT_ID, BUYER_EMAIL);

        assertNotNull(responses);

        verify(authorizationService).requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );
    }

    @Test
    void getRefundsByPaymentId_shouldAllowSupplierWithSupplierCapability() {

        authenticateSupplier();
        mockCompletePaymentOwnership();
        mockPaymentLookup();

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "No BUYER membership"
        )).when(authorizationService).requireOrganizationCapability(
                SUPPLIER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );

        when(refundRepository.findByPayment_IdOrderByCreatedAtDesc(PAYMENT_ID))
                .thenReturn(List.of());

        List<RefundResponse> responses =
                refundService.getRefundsByPaymentId(
                        PAYMENT_ID,
                        SUPPLIER_EMAIL
                );

        assertNotNull(responses);

        verify(authorizationService).requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        );
    }

    @Test
    void getRefundsByPaymentId_shouldAllowPlatformAdmin() {

        authenticateAdmin();
        mockPaymentLookup();

        when(refundRepository.findByPayment_IdOrderByCreatedAtDesc(PAYMENT_ID))
                .thenReturn(List.of());

        List<RefundResponse> responses =
                refundService.getRefundsByPaymentId(PAYMENT_ID, ADMIN_EMAIL);

        assertNotNull(responses);

        verify(authorizationService, never())
                .requireOrganizationCapability(
                        anyString(),
                        anyLong(),
                        any(OrganizationCapability.class)
                );
    }

    // --------------------------------------------------
    // Admin-only refund listing tests
    // --------------------------------------------------

    @Test
    void getAllRefunds_shouldRequirePlatformAdmin() {

        when(refundRepository.findAll()).thenReturn(List.of());

        List<RefundResponse> responses =
                refundService.getAllRefunds(ADMIN_EMAIL);

        assertNotNull(responses);

        verify(authorizationService).requirePlatformAdmin(ADMIN_EMAIL);
    }

    @Test
    void getRefundsByStatus_shouldRequirePlatformAdmin() {

        when(refundRepository.findByStatus(RefundStatus.REQUESTED))
                .thenReturn(List.of());

        List<RefundResponse> responses =
                refundService.getRefundsByStatus(
                        RefundStatus.REQUESTED,
                        ADMIN_EMAIL
                );

        assertNotNull(responses);

        verify(authorizationService).requirePlatformAdmin(ADMIN_EMAIL);
    }

    // --------------------------------------------------
    // Process refund tests
    // --------------------------------------------------

    @Test
    void processRefund_shouldChangeStatusToProcessing() {

        Refund refund = new Refund(
                payment,
                new BigDecimal("300.00"),
                "Damaged product"
        );

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        when(payment.getId()).thenReturn(PAYMENT_ID);

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.save(any(Refund.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefundResponse response =
                refundService.processRefund(REFUND_ID, ADMIN_EMAIL);

        assertEquals(RefundStatus.PROCESSING, response.status());

        verify(authorizationService).requirePlatformAdmin(ADMIN_EMAIL);
        verify(entityManager).refresh(refund);
        verify(refundRepository).save(refund);
    }

    @Test
    void processRefund_shouldRejectAlreadyProcessedRefund() {

        Refund refund = new Refund(
                payment,
                new BigDecimal("300.00"),
                "Damaged product"
        );

        refund.setStatus(RefundStatus.COMPLETED);

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        when(payment.getId()).thenReturn(PAYMENT_ID);

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.processRefund(REFUND_ID, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(refundRepository, never()).save(any(Refund.class));
    }

    // --------------------------------------------------
    // Complete refund tests
    // --------------------------------------------------

    @Test
    void completeRefund_shouldMarkPaymentAsRefundedWhenFullyRefunded() {

        Refund refund = new Refund(
                payment,
                PAYMENT_AMOUNT,
                "Full refund"
        );

        refund.setStatus(RefundStatus.PROCESSING);

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        when(payment.getId()).thenReturn(PAYMENT_ID);
        when(payment.getAmount()).thenReturn(PAYMENT_AMOUNT);

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.saveAndFlush(refund))
                .thenReturn(refund);

        when(refundRepository.sumRefundAmountsByPaymentAndStatuses(
                eq(PAYMENT_ID), anyList()))
                .thenReturn(PAYMENT_AMOUNT);

        RefundResponse response =
                refundService.completeRefund(REFUND_ID, ADMIN_EMAIL);

        assertEquals(RefundStatus.COMPLETED, response.status());

        verify(authorizationService).requirePlatformAdmin(ADMIN_EMAIL);
        verify(payment).setStatus(PaymentStatus.REFUNDED);
        verify(refundRepository).saveAndFlush(refund);
    }

    @Test
    void completeRefund_shouldKeepPaymentCompletedForPartialRefund() {

        Refund refund = new Refund(
                payment,
                new BigDecimal("300.00"),
                "Partial refund"
        );

        refund.setStatus(RefundStatus.PROCESSING);

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        when(payment.getId()).thenReturn(PAYMENT_ID);
        when(payment.getAmount()).thenReturn(PAYMENT_AMOUNT);

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.saveAndFlush(refund))
                .thenReturn(refund);

        when(refundRepository.sumRefundAmountsByPaymentAndStatuses(
                eq(PAYMENT_ID), anyList()))
                .thenReturn(new BigDecimal("300.00"));

        RefundResponse response =
                refundService.completeRefund(REFUND_ID, ADMIN_EMAIL);

        assertEquals(RefundStatus.COMPLETED, response.status());

        verify(payment, never()).setStatus(PaymentStatus.REFUNDED);
        verify(refundRepository).saveAndFlush(refund);
    }

    @Test
    void completeRefund_shouldRejectRequestedRefund() {

        Refund refund = new Refund(
                payment,
                new BigDecimal("300.00"),
                "Refund request"
        );

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        when(payment.getId()).thenReturn(PAYMENT_ID);

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.completeRefund(REFUND_ID, ADMIN_EMAIL)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(refundRepository, never()).saveAndFlush(any(Refund.class));
    }

    // --------------------------------------------------
    // Fail refund tests
    // --------------------------------------------------

    @Test
    void failRefund_shouldMarkRefundAsFailed() {

        Refund refund = new Refund(
                payment,
                new BigDecimal("300.00"),
                "Damaged product"
        );

        refund.setStatus(RefundStatus.PROCESSING);

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        when(payment.getId()).thenReturn(PAYMENT_ID);

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(refundRepository.save(refund))
                .thenReturn(refund);

        RefundResponse response = refundService.failRefund(
                REFUND_ID,
                "Payment gateway failure",
                ADMIN_EMAIL
        );

        assertEquals(RefundStatus.FAILED, response.status());
        assertEquals(
                "Payment gateway failure",
                response.failureReason()
        );

        verify(authorizationService).requirePlatformAdmin(ADMIN_EMAIL);
        verify(refundRepository).save(refund);
    }

    @Test
    void failRefund_shouldRejectBlankFailureReason() {

        Refund refund = new Refund(
                payment,
                new BigDecimal("300.00"),
                "Damaged product"
        );

        refund.setStatus(RefundStatus.PROCESSING);

        when(refundRepository.findById(REFUND_ID))
                .thenReturn(Optional.of(refund));

        when(payment.getId()).thenReturn(PAYMENT_ID);

        when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refundService.failRefund(
                        REFUND_ID,
                        "   ",
                        ADMIN_EMAIL
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        verify(refundRepository, never()).save(any(Refund.class));
    }
}