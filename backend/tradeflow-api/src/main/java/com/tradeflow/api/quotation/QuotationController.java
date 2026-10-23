package com.tradeflow.api.quotation;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/quotations")
@Validated
public class QuotationController {

    private final QuotationService quotationService;

    public QuotationController(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    // Submit a quotation as the authenticated supplier
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Quotation createQuotation(
            @Valid @RequestBody QuotationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quotationService.createQuotation(
                request,
                jwt.getSubject()
        );
    }

    // Get quotations for a procurement request
    @GetMapping("/procurement-request/{procurementRequestId}")
    public List<Quotation> getQuotationsByProcurementRequest(
            @PathVariable Long procurementRequestId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quotationService.getQuotationsByProcurementRequest(
                procurementRequestId,
                jwt.getSubject()
        );
    }

    // Get a quotation by ID
    @GetMapping("/{id}")
    public Quotation getQuotationById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quotationService.getQuotationById(
                id,
                jwt.getSubject()
        );
    }

    // Accept a quotation as the owning buyer
    @PatchMapping("/{id}/accept")
    public Quotation acceptQuotation(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quotationService.acceptQuotation(
                id,
                jwt.getSubject()
        );
    }

    // Reject a quotation as the owning buyer
    @PatchMapping("/{id}/reject")
    public Quotation rejectQuotation(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quotationService.rejectQuotation(
                id,
                jwt.getSubject()
        );
    }
}