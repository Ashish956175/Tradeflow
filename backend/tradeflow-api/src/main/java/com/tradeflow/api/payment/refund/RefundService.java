package com.tradeflow.api.payment.refund;

import com.tradeflow.api.payment.Payment;
import com.tradeflow.api.payment.PaymentRepository;
import com.tradeflow.api.payment.PaymentStatus;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public RefundService(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            EntityManager entityManager
    ) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    // Buyer requests a full or partial refund.
    @Transactional
    public RefundResponse requestRefund(
            RefundRequest request,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.BUYER);

        Payment payment = paymentRepository
                .findByIdForUpdate(request.paymentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));

        verifyBuyerOwnsPayment(user, payment);

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only completed payments can be refunded"
            );
        }

        BigDecimal reservedAmount =
                refundRepository.sumRefundAmountsByPaymentAndStatuses(
                        payment.getId(),
                        List.of(
                                RefundStatus.REQUESTED,
                                RefundStatus.PROCESSING,
                                RefundStatus.COMPLETED
                        )
                );

        BigDecimal refundableAmount =
                payment.getAmount().subtract(reservedAmount);

        if (request.amount().compareTo(refundableAmount) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Refund amount exceeds the available refundable balance"
            );
        }

        Refund refund = new Refund(
                payment,
                request.amount(),
                request.reason().trim()
        );

        Refund savedRefund = refundRepository.save(refund);

        return RefundResponse.from(savedRefund);
    }

    // Admin retrieves all refund requests.
    @Transactional(readOnly = true)
    public List<RefundResponse> getAllRefunds(String authenticatedEmail) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        return refundRepository.findAll()
                .stream()
                .map(RefundResponse::from)
                .toList();
    }

    // Retrieve refund details with ownership verification.
    @Transactional(readOnly = true)
    public RefundResponse getRefundById(
            Long refundId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        Refund refund = findRefund(refundId);

        verifyRefundAccess(user, refund);

        return RefundResponse.from(refund);
    }

    // Retrieve refund history for a payment.
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByPaymentId(
            Long paymentId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));

        verifyPaymentAccess(user, payment);

        return refundRepository
                .findByPayment_IdOrderByCreatedAtDesc(paymentId)
                .stream()
                .map(RefundResponse::from)
                .toList();
    }

    // Admin retrieves refunds by status.
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByStatus(
            RefundStatus status,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        return refundRepository.findByStatus(status)
                .stream()
                .map(RefundResponse::from)
                .toList();
    }

    // Admin approves a refund for processing.
    @Transactional
    public RefundResponse processRefund(
            Long refundId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        Refund refund = findRefundWithPaymentLock(refundId);

        if (refund.getStatus() != RefundStatus.REQUESTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only requested refunds can be processed"
            );
        }

        refund.setStatus(RefundStatus.PROCESSING);

        return RefundResponse.from(refundRepository.save(refund));
    }

    // Admin marks a refund as completed.
    @Transactional
    public RefundResponse completeRefund(
            Long refundId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        Refund refund = findRefundWithPaymentLock(refundId);

        if (refund.getStatus() != RefundStatus.PROCESSING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only processing refunds can be completed"
            );
        }

        refund.setStatus(RefundStatus.COMPLETED);

        // Flush the updated refund status before calculating the total.
        refundRepository.saveAndFlush(refund);

        Payment payment = refund.getPayment();

        BigDecimal completedAmount =
                refundRepository.sumRefundAmountsByPaymentAndStatuses(
                        payment.getId(),
                        List.of(RefundStatus.COMPLETED)
                );

        if (completedAmount.compareTo(payment.getAmount()) == 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
        }

        return RefundResponse.from(refund);
    }

    // Admin marks a refund as failed.
    @Transactional
    public RefundResponse failRefund(
            Long refundId,
            String failureReason,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.ADMIN);

        Refund refund = findRefundWithPaymentLock(refundId);

        if (refund.getStatus() != RefundStatus.PROCESSING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only processing refunds can be marked as failed"
            );
        }

        if (failureReason == null || failureReason.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Failure reason is required"
            );
        }

        refund.setStatus(RefundStatus.FAILED);
        refund.setFailureReason(failureReason.trim());

        return RefundResponse.from(refundRepository.save(refund));
    }

    // Find refund or throw 404.
    private Refund findRefund(Long refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Refund not found"
                ));
    }

    // Lock payment before loading refund for state transitions.
    // Lock payment before refreshing refund for state transitions.
    private Refund findRefundWithPaymentLock(Long refundId) {
        Refund refund = findRefund(refundId);

        Long paymentId = refund.getPayment().getId();

        paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));

        // Refresh the refund to avoid using stale state
        // after waiting for another transaction to release the payment lock.
        entityManager.refresh(refund);

        return refund;
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

    private void verifyBuyerOwnsPayment(User user, Payment payment) {
        Organization buyerOrganization = requireOrganization(user);

        Long orderBuyerOrganizationId = payment.getPurchaseOrder()
                .getQuotation()
                .getProcurementRequest()
                .getBuyerOrganization()
                .getId();

        if (!buyerOrganization.getId().equals(orderBuyerOrganizationId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot request a refund for another buyer's payment"
            );
        }
    }

    private void verifyPaymentAccess(User user, Payment payment) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        Organization organization = requireOrganization(user);

        Long buyerOrganizationId = payment.getPurchaseOrder()
                .getQuotation()
                .getProcurementRequest()
                .getBuyerOrganization()
                .getId();

        Long supplierOrganizationId = payment.getPurchaseOrder()
                .getQuotation()
                .getSupplier()
                .getOrganization()
                .getId();

        if (user.getRole() == UserRole.BUYER
                && organization.getId().equals(buyerOrganizationId)) {
            return;
        }

        if (user.getRole() == UserRole.SUPPLIER
                && organization.getId().equals(supplierOrganizationId)) {
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this refund"
        );
    }

    private void verifyRefundAccess(User user, Refund refund) {
        verifyPaymentAccess(user, refund.getPayment());
    }
}