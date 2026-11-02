package com.tradeflow.api.payment.refund;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    // Buyer requests a refund.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse requestRefund(
            @Valid @RequestBody RefundRequest request,
            Authentication authentication
    ) {
        return refundService.requestRefund(
                request,
                authentication.getName()
        );
    }

    // Admin retrieves all refunds.
    @GetMapping
    public List<RefundResponse> getAllRefunds(
            Authentication authentication
    ) {
        return refundService.getAllRefunds(authentication.getName());
    }

    // Retrieve refund by ID.
    @GetMapping("/{refundId}")
    public RefundResponse getRefundById(
            @PathVariable Long refundId,
            Authentication authentication
    ) {
        return refundService.getRefundById(
                refundId,
                authentication.getName()
        );
    }

    // Retrieve refund history for a payment.
    @GetMapping("/payment/{paymentId}")
    public List<RefundResponse> getRefundsByPaymentId(
            @PathVariable Long paymentId,
            Authentication authentication
    ) {
        return refundService.getRefundsByPaymentId(
                paymentId,
                authentication.getName()
        );
    }

    // Admin filters refunds by status.
    @GetMapping("/status/{status}")
    public List<RefundResponse> getRefundsByStatus(
            @PathVariable RefundStatus status,
            Authentication authentication
    ) {
        return refundService.getRefundsByStatus(
                status,
                authentication.getName()
        );
    }

    // Admin moves requested refund to processing.
    @PatchMapping("/{refundId}/process")
    public RefundResponse processRefund(
            @PathVariable Long refundId,
            Authentication authentication
    ) {
        return refundService.processRefund(
                refundId,
                authentication.getName()
        );
    }

    // Admin completes a refund.
    @PatchMapping("/{refundId}/complete")
    public RefundResponse completeRefund(
            @PathVariable Long refundId,
            Authentication authentication
    ) {
        return refundService.completeRefund(
                refundId,
                authentication.getName()
        );
    }

    // Admin marks a refund as failed.
    @PatchMapping("/{refundId}/fail")
    public RefundResponse failRefund(
            @PathVariable Long refundId,
            @RequestParam String reason,
            Authentication authentication
    ) {
        return refundService.failRefund(
                refundId,
                reason,
                authentication.getName()
        );
    }
}