package com.tradeflow.api.payment.refund;

import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.payment.Payment;
import com.tradeflow.api.payment.PaymentRepository;
import com.tradeflow.api.payment.PaymentStatus;
import com.tradeflow.api.security.MembershipAuthorizationService;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRole;

import jakarta.persistence.EntityManager;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final MembershipAuthorizationService authorizationService;
    private final EntityManager entityManager;

    public RefundService(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            MembershipAuthorizationService authorizationService,
            EntityManager entityManager
    ) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.authorizationService = authorizationService;
        this.entityManager = entityManager;
    }

    // Buyer requests a full or partial refund.
    @Transactional
    public RefundResponse requestRefund(
            RefundRequest request,
            String authenticatedEmail
    ) {
        User user = authorizationService.getAuthenticatedUser(authenticatedEmail);

        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Platform administrators cannot request refunds"
            );
        }

        Payment payment = paymentRepository
                .findByIdForUpdate(request.paymentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));

        Long buyerOrganizationId = payment
                .getPurchaseOrder()
                .getQuotation()
                .getProcurementRequest()
                .getBuyerOrganization()
                .getId();

        authorizationService.requireOrganizationCapability(
                authenticatedEmail,
                buyerOrganizationId,
                OrganizationCapability.BUYER
        );

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
        authorizationService.requirePlatformAdmin(authenticatedEmail);

        return refundRepository.findAll()
                .stream()
                .map(RefundResponse::from)
                .toList();
    }

    // Retrieve refund details with authorization verification.
    @Transactional(readOnly = true)
    public RefundResponse getRefundById(
            Long refundId,
            String authenticatedEmail
    ) {
        Refund refund = findRefund(refundId);

        verifyRefundAccess(authenticatedEmail, refund);

        return RefundResponse.from(refund);
    }

    // Retrieve refund history for a payment.
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByPaymentId(
            Long paymentId,
            String authenticatedEmail
    ) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));

        verifyPaymentAccess(authenticatedEmail, payment);

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
        authorizationService.requirePlatformAdmin(authenticatedEmail);

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
        authorizationService.requirePlatformAdmin(authenticatedEmail);

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
        authorizationService.requirePlatformAdmin(authenticatedEmail);

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
        authorizationService.requirePlatformAdmin(authenticatedEmail);

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

    // Verify whether the authenticated user can access the payment.
    private void verifyPaymentAccess(
            String authenticatedEmail,
            Payment payment
    ) {
        User user = authorizationService.getAuthenticatedUser(authenticatedEmail);

        // Platform administrators can read all refunds.
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

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

        // First, check whether the user has BUYER access.
        try {
            authorizationService.requireOrganizationCapability(
                    authenticatedEmail,
                    buyerOrganizationId,
                    OrganizationCapability.BUYER
            );

            return;

        } catch (ResponseStatusException exception) {
            // Only a 403 permits checking supplier access.
            // Do not swallow 401, 404, or other errors.
            if (exception.getStatusCode().value()
                    != HttpStatus.FORBIDDEN.value()) {
                throw exception;
            }
        }

        // Then, check whether the user has SUPPLIER access.
        authorizationService.requireOrganizationCapability(
                authenticatedEmail,
                supplierOrganizationId,
                OrganizationCapability.SUPPLIER
        );
    }

    // Verify whether the authenticated user can access the refund.
    private void verifyRefundAccess(
            String authenticatedEmail,
            Refund refund
    ) {
        verifyPaymentAccess(authenticatedEmail, refund.getPayment());
    }
}
