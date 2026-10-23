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

    // Create a payment for a confirmed order owned by the buyer.
    @Transactional
    public Payment createPayment(
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

        return paymentRepository.save(payment);
    }

    // Only administrators can view every payment.
    @Transactional(readOnly = true)
    public List<Payment> getAllPayments(String authenticatedEmail) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        return paymentRepository.findAll();
    }

    // Retrieve one payment after checking organization ownership.
    @Transactional(readOnly = true)
    public Payment getPaymentById(
            Long paymentId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));

        verifyPaymentAccess(user, payment.getPurchaseOrder());

        return payment;
    }

    // Retrieve a payment for an order the user is authorized to access.
    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(
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

        return paymentRepository.findByPurchaseOrder_Id(purchaseOrderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found for this purchase order"
                ));
    }

    // Only administrators can query payments across organizations by status.
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByStatus(
            PaymentStatus status,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        return paymentRepository.findByStatus(status);
    }

    // For prototype/testing use: only an administrator can update the status.
    @Transactional
    public Payment markPaymentAsCompleted(
            Long paymentId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        Payment payment = findPayment(paymentId);
        requirePendingStatus(payment);

        payment.setStatus(PaymentStatus.COMPLETED);

        return paymentRepository.save(payment);
    }

    // For prototype/testing use: only an administrator can update the status.
    @Transactional
    public Payment markPaymentAsFailed(
            Long paymentId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        Payment payment = findPayment(paymentId);
        requirePendingStatus(payment);

        payment.setStatus(PaymentStatus.FAILED);

        return paymentRepository.save(payment);
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));
    }

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

    private void requireRole(User user, UserRole requiredRole) {
        if (user.getRole() != requiredRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to perform this action"
            );
        }
    }

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

    private void verifyPaymentAccess(
            User user,
            PurchaseOrder order
    ) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        Organization organization = requireOrganization(user);

        if (user.getRole() == UserRole.BUYER) {
            Long buyerOrganizationId = order.getQuotation()
                    .getProcurementRequest()
                    .getBuyerOrganization()
                    .getId();

            if (organization.getId().equals(buyerOrganizationId)) {
                return;
            }
        }

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

    private void requirePendingStatus(Payment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending payments can be marked completed or failed"
            );
        }
    }
}