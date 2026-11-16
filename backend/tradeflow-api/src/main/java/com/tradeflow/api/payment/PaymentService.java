package com.tradeflow.api.payment;

import com.tradeflow.api.order.OrderStatus;
import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.order.PurchaseOrderRepository;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.security.MembershipAuthorizationService;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRole;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final MembershipAuthorizationService membershipAuthorizationService;

    public PaymentService(
            PaymentRepository paymentRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            MembershipAuthorizationService membershipAuthorizationService
    ) {
        this.paymentRepository = paymentRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.membershipAuthorizationService = membershipAuthorizationService;
    }

    // Create payment for a confirmed order owned by the buyer.
    @Transactional
    public PaymentResponse createPayment(
            PaymentRequest request,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        // Platform administrators cannot create payments on behalf of buyers.
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to perform this action"
            );
        }

        PurchaseOrder order = purchaseOrderRepository
                .findById(request.purchaseOrderId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        Long buyerOrganizationId = order.getQuotation()
                .getProcurementRequest()
                .getBuyerOrganization()
                .getId();

        // Require active BUYER capability in the buyer organization.
        membershipAuthorizationService.requireOrganizationCapability(
                authenticatedEmail,
                buyerOrganizationId,
                OrganizationCapability.BUYER
        );

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment can only be created for a confirmed order"
            );
        }

        if (paymentRepository.existsByPurchaseOrder_Id(order.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A payment already exists for this purchase order"
            );
        }

        String paymentMethod = request.paymentMethod()
                .trim()
                .toUpperCase(Locale.ROOT);

        Payment payment = new Payment(order, paymentMethod);

        Payment savedPayment = paymentRepository.save(payment);

        return PaymentResponse.from(savedPayment);
    }

    // Only platform administrators can retrieve all payments.
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments(
            String authenticatedEmail
    ) {
        membershipAuthorizationService.requirePlatformAdmin(
                authenticatedEmail
        );

        return paymentRepository.findAll()
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }

    // Retrieve payment by ID after verifying organization ownership.
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(
            Long paymentId,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        Payment payment = findPayment(paymentId);

        verifyPaymentAccess(
                authenticatedEmail,
                user,
                payment.getPurchaseOrder()
        );

        return PaymentResponse.from(payment);
    }

    // Retrieve payment using purchase order ID.
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(
            Long purchaseOrderId,
            String authenticatedEmail
    ) {
        User user = membershipAuthorizationService
                .getAuthenticatedUser(authenticatedEmail);

        PurchaseOrder order = purchaseOrderRepository
                .findById(purchaseOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        verifyPaymentAccess(
                authenticatedEmail,
                user,
                order
        );

        Payment payment = paymentRepository
                .findByPurchaseOrder_Id(purchaseOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found for this purchase order"
                ));

        return PaymentResponse.from(payment);
    }

    // Only platform administrators can filter payments by status.
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByStatus(
            PaymentStatus status,
            String authenticatedEmail
    ) {
        membershipAuthorizationService.requirePlatformAdmin(
                authenticatedEmail
        );

        return paymentRepository.findByStatus(status)
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }

    // Simulated payment completion for prototype/testing.
    @Transactional
    public PaymentResponse markPaymentAsCompleted(
            Long paymentId,
            String authenticatedEmail
    ) {
        membershipAuthorizationService.requirePlatformAdmin(
                authenticatedEmail
        );

        Payment payment = findPayment(paymentId);

        requirePendingStatus(payment);

        payment.setStatus(PaymentStatus.COMPLETED);

        Payment savedPayment = paymentRepository.save(payment);

        return PaymentResponse.from(savedPayment);
    }

    // Simulated payment failure for prototype/testing.
    @Transactional
    public PaymentResponse markPaymentAsFailed(
            Long paymentId,
            String authenticatedEmail
    ) {
        membershipAuthorizationService.requirePlatformAdmin(
                authenticatedEmail
        );

        Payment payment = findPayment(paymentId);

        requirePendingStatus(payment);

        payment.setStatus(PaymentStatus.FAILED);

        Payment savedPayment = paymentRepository.save(payment);

        return PaymentResponse.from(savedPayment);
    }

    // Find payment or throw 404.
    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));
    }

    // Verify buyer, supplier or platform administrator can access the payment.
    private void verifyPaymentAccess(
            String authenticatedEmail,
            User user,
            PurchaseOrder order
    ) {

        // Platform administrators have unrestricted payment read access.
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        Long buyerOrganizationId = order.getQuotation()
                .getProcurementRequest()
                .getBuyerOrganization()
                .getId();

        Long supplierOrganizationId = order.getQuotation()
                .getSupplier()
                .getOrganization()
                .getId();

        // First, try BUYER capability in the buyer organization.
        try {
            membershipAuthorizationService.requireOrganizationCapability(
                    authenticatedEmail,
                    buyerOrganizationId,
                    OrganizationCapability.BUYER
            );

            return;

        } catch (ResponseStatusException exception) {

            // Only try supplier authorization when buyer authorization is denied.
            if (exception.getStatusCode() != HttpStatus.FORBIDDEN) {
                throw exception;
            }
        }

        // Next, try SUPPLIER capability in the supplier organization.
        try {
            membershipAuthorizationService.requireOrganizationCapability(
                    authenticatedEmail,
                    supplierOrganizationId,
                    OrganizationCapability.SUPPLIER
            );

            return;

        } catch (ResponseStatusException exception) {

            if (exception.getStatusCode() != HttpStatus.FORBIDDEN) {
                throw exception;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this payment"
        );
    }

    // Only pending payments can be completed or failed.
    private void requirePendingStatus(Payment payment) {

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending payments can be marked completed or failed"
            );
        }
    }
}
