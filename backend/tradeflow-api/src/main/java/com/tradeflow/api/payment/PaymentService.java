package com.tradeflow.api.payment;

import com.tradeflow.api.order.OrderStatus;
import com.tradeflow.api.order.PurchaseOrder;
import com.tradeflow.api.order.PurchaseOrderRepository;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
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
    private final UserRepository userRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            UserRepository userRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.userRepository = userRepository;
    }

    // Create payment for a confirmed order owned by the buyer.
    @Transactional
    public PaymentResponse createPayment(
            PaymentRequest request,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.BUYER);

        PurchaseOrder order = purchaseOrderRepository
                .findById(request.purchaseOrderId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        verifyBuyerOwnsOrder(user, order);

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

    // Only administrators can retrieve all payments.
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments(
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

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
        User user = getAuthenticatedUser(authenticatedEmail);

        Payment payment = findPayment(paymentId);

        verifyPaymentAccess(user, payment.getPurchaseOrder());

        return PaymentResponse.from(payment);
    }

    // Retrieve payment using purchase order ID.
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(
            Long purchaseOrderId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        PurchaseOrder order = purchaseOrderRepository
                .findById(purchaseOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Purchase order not found"
                ));

        verifyPaymentAccess(user, order);

        Payment payment = paymentRepository
                .findByPurchaseOrder_Id(purchaseOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found for this purchase order"
                ));

        return PaymentResponse.from(payment);
    }

    // Only administrators can filter payments by status.
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByStatus(
            PaymentStatus status,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

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
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

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
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

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

    // Retrieve authenticated and enabled user.
    private User getAuthenticatedUser(String email) {

        String normalizedEmail = email.trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        if (!user.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is disabled"
            );
        }

        return user;
    }

    // Verify user role.
    private void requireRole(
            User user,
            UserRole requiredRole
    ) {
        if (user.getRole() != requiredRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to perform this action"
            );
        }
    }

    // Retrieve organization associated with the user.
    private Organization requireOrganization(User user) {

        Organization organization = user.getOrganization();

        if (organization == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is not associated with an organization"
            );
        }

        return organization;
    }

    // Verify buyer owns the purchase order.
    private void verifyBuyerOwnsOrder(
            User user,
            PurchaseOrder order
    ) {
        Organization buyerOrganization = requireOrganization(user);

        Long orderBuyerOrganizationId = order.getQuotation()
                .getProcurementRequest()
                .getBuyerOrganization()
                .getId();

        if (!buyerOrganization.getId().equals(orderBuyerOrganizationId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot create a payment for another buyer's order"
            );
        }
    }

    // Verify buyer, supplier or administrator can access the payment.
    private void verifyPaymentAccess(
            User user,
            PurchaseOrder order
    ) {

        // Administrators have unrestricted payment access.
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        Organization organization = requireOrganization(user);

        // Buyer organization verification.
        if (user.getRole() == UserRole.BUYER) {

            Long buyerOrganizationId = order.getQuotation()
                    .getProcurementRequest()
                    .getBuyerOrganization()
                    .getId();

            if (organization.getId().equals(buyerOrganizationId)) {
                return;
            }
        }

        // Supplier organization verification.
        if (user.getRole() == UserRole.SUPPLIER) {

            Long supplierOrganizationId = order.getQuotation()
                    .getSupplier()
                    .getOrganization()
                    .getId();

            if (organization.getId().equals(supplierOrganizationId)) {
                return;
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