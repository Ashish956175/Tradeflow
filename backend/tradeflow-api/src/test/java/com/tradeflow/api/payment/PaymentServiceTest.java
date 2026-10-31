package com.tradeflow.api.payment;

import com.tradeflow.api.order.OrderStatus;
import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.order.PurchaseOrderRepository;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.quotation.Quotation;
import com.tradeflow.api.supplier.Supplier;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String BUYER_EMAIL = "buyer@tradeflow.test";
    private static final String ADMIN_EMAIL = "admin@tradeflow.test";
    private static final String SUPPLIER_EMAIL = "supplier@tradeflow.test";

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
    private void authenticateSupplier() {
        User supplier = mock(User.class);

        when(userRepository.findByEmail(SUPPLIER_EMAIL))
                .thenReturn(Optional.of(supplier));

        when(supplier.isEnabled()).thenReturn(true);
        when(supplier.getRole()).thenReturn(UserRole.SUPPLIER);

        Organization organization = mock(Organization.class);
        when(organization.getId()).thenReturn(200L);

        when(supplier.getOrganization()).thenReturn(organization);
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
    private void configurePaymentMapping() {
        when(payment.getId()).thenReturn(PAYMENT_ID);
        when(payment.getPurchaseOrder()).thenReturn(order);
        when(order.getId()).thenReturn(ORDER_ID);
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

    @Test
    void createPayment_shouldSuccessfullyCreatePayment() {
        authenticateBuyer();
        configureBuyerOwnership();

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

    @Test
    void markPaymentAsCompleted_shouldSuccessfullyCompletePendingPayment() {
        authenticateAdmin();
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

    @Test
    void markPaymentAsFailed_shouldSuccessfullyFailPendingPayment() {
        authenticateAdmin();
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

    @Test
    void getPaymentById_shouldAllowSupplierFromOwningOrganization() {
        authenticateSupplier();
        configurePaymentMapping();

        Organization supplierOrganization = mock(Organization.class);
        when(supplierOrganization.getId()).thenReturn(200L);

        var quotation = mock(
                com.tradeflow.api.quotation.Quotation.class
        );

        var supplier = mock(
                com.tradeflow.api.supplier.Supplier.class
        );

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(payment.getPurchaseOrder()).thenReturn(order);
        when(order.getQuotation()).thenReturn(quotation);
        when(quotation.getSupplier()).thenReturn(supplier);
        when(supplier.getOrganization()).thenReturn(supplierOrganization);

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


        Organization anotherSupplierOrganization = mock(Organization.class);
        when(anotherSupplierOrganization.getId()).thenReturn(300L);

        var quotation = mock(
                com.tradeflow.api.quotation.Quotation.class
        );

        var supplier = mock(
                com.tradeflow.api.supplier.Supplier.class
        );

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(payment.getPurchaseOrder()).thenReturn(order);
        when(order.getQuotation()).thenReturn(quotation);
        when(quotation.getSupplier()).thenReturn(supplier);
        when(supplier.getOrganization())
                .thenReturn(anotherSupplierOrganization);

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
        configureBuyerOwnership();
        configurePaymentMapping();

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));

        when(payment.getPurchaseOrder()).thenReturn(order);

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


        Organization orderBuyerOrganization = mock(Organization.class);
        Organization anotherOrganization = mock(Organization.class);
        Quotation quotation = mock(Quotation.class);
        ProcurementRequest procurementRequest = mock(ProcurementRequest.class);

        when(orderBuyerOrganization.getId()).thenReturn(100L);
        when(anotherOrganization.getId()).thenReturn(999L);

        when(buyer.getOrganization()).thenReturn(anotherOrganization);

        when(order.getQuotation()).thenReturn(quotation);
        when(quotation.getProcurementRequest()).thenReturn(procurementRequest);
        when(procurementRequest.getBuyerOrganization())
                .thenReturn(orderBuyerOrganization);

        when(paymentRepository.findById(PAYMENT_ID))
                .thenReturn(Optional.of(payment));
        when(payment.getPurchaseOrder()).thenReturn(order);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.getPaymentById(PAYMENT_ID, BUYER_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }
    @Test
    void getPaymentById_shouldAllowAdminAccess() {
        authenticateAdmin();
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

    @Test
    void getPaymentByOrderId_shouldAllowBuyerFromOwningOrganization() {
        authenticateBuyer();
        configureBuyerOwnership();
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
        configurePaymentMapping();

        Organization supplierOrganization = mock(Organization.class);
        Quotation quotation = mock(Quotation.class);
        Supplier supplierEntity = mock(Supplier.class);

        when(supplierOrganization.getId()).thenReturn(200L);
        when(supplierEntity.getOrganization()).thenReturn(supplierOrganization);
        when(quotation.getSupplier()).thenReturn(supplierEntity);
        when(order.getQuotation()).thenReturn(quotation);

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


        Organization anotherOrganization = mock(Organization.class);
        Quotation quotation = mock(Quotation.class);
        Supplier supplierEntity = mock(Supplier.class);

        when(anotherOrganization.getId()).thenReturn(300L);
        when(supplierEntity.getOrganization()).thenReturn(anotherOrganization);
        when(quotation.getSupplier()).thenReturn(supplierEntity);
        when(order.getQuotation()).thenReturn(quotation);

        when(purchaseOrderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.getPaymentByOrderId(ORDER_ID, SUPPLIER_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(purchaseOrderRepository).findById(ORDER_ID);
        verify(paymentRepository, never()).findByPurchaseOrder_Id(ORDER_ID);
    }

    @Test
    void getPaymentsByStatus_shouldAllowAdminAccess() {
        authenticateAdmin();
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
        authenticateBuyer();


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
    @Test
    void getAllPayments_shouldAllowAdminAccess() {
        authenticateAdmin();
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
        authenticateBuyer();


        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.getAllPayments(BUYER_EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(paymentRepository, never()).findAll();
    }

}