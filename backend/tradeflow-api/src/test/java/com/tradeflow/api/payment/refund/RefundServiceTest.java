package com.tradeflow.api.payment.refund;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.payment.Payment;
import com.tradeflow.api.payment.PaymentRepository;
import com.tradeflow.api.payment.PaymentStatus;

import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.quotation.Quotation;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Long REFUND_ID = 1L;
    private static final Long PAYMENT_ID = 10L;
    private static final Long ORGANIZATION_ID = 100L;

    private static final String BUYER_EMAIL = "buyer@test.com";
    private static final String ADMIN_EMAIL = "admin@test.com";

    private static final BigDecimal PAYMENT_AMOUNT =
            new BigDecimal("1000.00");

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private User buyer;

    @Mock
    private User admin;

    @Mock
    private Organization buyerOrganization;

    @Mock
    private Payment payment;

    @Mock
    private PurchaseOrder purchaseOrder;

    @Mock
    private Quotation quotation;

    @Mock
    private ProcurementRequest procurementRequest;

    @InjectMocks
    private RefundService refundService;

    // --------------------------------------------------
    // Authentication helpers
    // --------------------------------------------------

    private void configureBuyer() {
        when(buyer.isEnabled()).thenReturn(true);
        when(buyer.getRole()).thenReturn(UserRole.BUYER);
        when(buyer.getOrganization()).thenReturn(buyerOrganization);
        when(buyerOrganization.getId()).thenReturn(ORGANIZATION_ID);
    }

    private void configureAdmin() {
        when(admin.isEnabled()).thenReturn(true);
        when(admin.getRole()).thenReturn(UserRole.ADMIN);
    }

    private void authenticateBuyer() {
        configureBuyer();

        when(userRepository.findByEmail(BUYER_EMAIL))
                .thenReturn(Optional.of(buyer));
    }

    private void authenticateAdmin() {
        configureAdmin();

        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(admin));
    }

    // --------------------------------------------------
    // Payment ownership helper
    // --------------------------------------------------

    private void mockBuyerPaymentOwnership() {
        when(payment.getPurchaseOrder()).thenReturn(purchaseOrder);

        when(purchaseOrder.getQuotation()).thenReturn(quotation);

        when(quotation.getProcurementRequest())
                .thenReturn(procurementRequest);

        when(procurementRequest.getBuyerOrganization())
                .thenReturn(buyerOrganization);
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

        // This test checks payment status before payment ID or amount
        // is needed by the service.
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
    }

    @Test
    void requestRefund_shouldRejectDisabledBuyer() {

        when(buyer.isEnabled()).thenReturn(false);

        when(userRepository.findByEmail(BUYER_EMAIL))
                .thenReturn(Optional.of(buyer));

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
    }

    @Test
    void requestRefund_shouldRejectBuyerWithoutOrganization() {

        when(buyer.isEnabled()).thenReturn(true);
        when(buyer.getRole()).thenReturn(UserRole.BUYER);
        when(buyer.getOrganization()).thenReturn(null);

        when(userRepository.findByEmail(BUYER_EMAIL))
                .thenReturn(Optional.of(buyer));

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

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(refundRepository, never()).save(any(Refund.class));
    }

    // --------------------------------------------------
    // Process refund tests
    // --------------------------------------------------

    @Test
    void processRefund_shouldChangeStatusToProcessing() {

        authenticateAdmin();

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

        verify(entityManager).refresh(refund);
        verify(refundRepository).save(refund);
    }

    @Test
    void processRefund_shouldRejectAlreadyProcessedRefund() {

        authenticateAdmin();

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

        authenticateAdmin();

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

        verify(payment).setStatus(PaymentStatus.REFUNDED);
        verify(refundRepository).saveAndFlush(refund);
    }

    @Test
    void completeRefund_shouldKeepPaymentCompletedForPartialRefund() {

        authenticateAdmin();

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

        authenticateAdmin();

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

        authenticateAdmin();

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

        verify(refundRepository).save(refund);
    }

    @Test
    void failRefund_shouldRejectBlankFailureReason() {

        authenticateAdmin();

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
