package com.tradeflow.api.payment;

import com.tradeflow.api.order.OrderStatus;
import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.order.PurchaseOrderRepository;
import com.tradeflow.api.organization.Organization;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String BUYER_EMAIL = "buyer@tradeflow.test";
    private static final String ADMIN_EMAIL = "admin@tradeflow.test";

    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 10L;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PaymentService paymentService;

    private User buyer;
    private User admin;
    private PurchaseOrder order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        buyer = mock(User.class);
        admin = mock(User.class);
        order = mock(PurchaseOrder.class);
        payment = mock(Payment.class);
    }

    private void authenticateBuyer() {
        when(userRepository.findByEmail(BUYER_EMAIL))
                .thenReturn(Optional.of(buyer));

        when(buyer.isEnabled()).thenReturn(true);
        when(buyer.getRole()).thenReturn(UserRole.BUYER);
    }

    private void authenticateAdmin() {
        when(userRepository.findByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(admin));

        when(admin.isEnabled()).thenReturn(true);
        when(admin.getRole()).thenReturn(UserRole.ADMIN);
    }

    private void configureBuyerOwnership() {
        Organization organization = mock(Organization.class);
        when(organization.getId()).thenReturn(100L);
        when(buyer.getOrganization()).thenReturn(organization);

        var quotation = mock(
                com.tradeflow.api.quotation.Quotation.class
        );

        var procurementRequest = mock(
                com.tradeflow.api.procurement.ProcurementRequest.class
        );

        Organization buyerOrganization = mock(Organization.class);
        when(buyerOrganization.getId()).thenReturn(100L);

        when(order.getQuotation()).thenReturn(quotation);
        when(quotation.getProcurementRequest())
                .thenReturn(procurementRequest);
        when(procurementRequest.getBuyerOrganization())
                .thenReturn(buyerOrganization);
    }

    @Test
    void createPayment_shouldRejectUnauthenticatedUser() {
        when(userRepository.findByEmail(BUYER_EMAIL))
                .thenReturn(Optional.empty());

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
        authenticateAdmin();

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

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void createPayment_shouldRejectUnconfirmedOrder() {
        authenticateBuyer();
        configureBuyerOwnership();

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        when(order.getStatus()).thenReturn(OrderStatus.CREATED);

        PaymentRequest request = new PaymentRequest(ORDER_ID, "UPI");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(request, BUYER_EMAIL)
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void markPaymentAsCompleted_shouldRejectNonAdmin() {
        authenticateBuyer();

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
        authenticateAdmin();

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

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void markPaymentAsCompleted_shouldRejectMissingPayment() {
        authenticateAdmin();

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
    void markPaymentAsFailed_shouldRejectNonPendingPayment() {
        authenticateAdmin();

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

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void createPayment_shouldRejectDuplicatePayment() {
        authenticateBuyer();
        configureBuyerOwnership();

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

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void createPayment_shouldRejectDisabledBuyer() {
        when(userRepository.findByEmail(BUYER_EMAIL))
                .thenReturn(Optional.of(buyer));

        when(buyer.isEnabled()).thenReturn(false);

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

        Organization buyerOrganization = mock(Organization.class);
        when(buyerOrganization.getId()).thenReturn(100L);
        when(buyer.getOrganization()).thenReturn(buyerOrganization);

        var quotation = mock(
                com.tradeflow.api.quotation.Quotation.class
        );

        var procurementRequest = mock(
                com.tradeflow.api.procurement.ProcurementRequest.class
        );

        Organization anotherBuyerOrganization = mock(Organization.class);
        when(anotherBuyerOrganization.getId()).thenReturn(200L);

        when(order.getQuotation()).thenReturn(quotation);
        when(quotation.getProcurementRequest())
                .thenReturn(procurementRequest);
        when(procurementRequest.getBuyerOrganization())
                .thenReturn(anotherBuyerOrganization);

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        PaymentRequest request = new PaymentRequest(ORDER_ID, "UPI");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.createPayment(request, BUYER_EMAIL)
        );

        assertEquals(403, exception.getStatusCode().value());

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void markPaymentAsFailed_shouldRejectCompletedPayment() {
        authenticateAdmin();

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

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

}