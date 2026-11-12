package com.tradeflow.api.quotation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quotations")
@Validated
public class QuotationController {

    private final QuotationService quotationService;

    public QuotationController(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    // Submit a quotation as an authorized supplier organization
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Quotation createQuotation(
            @Valid @RequestBody QuotationRequest request,
            @RequestHeader("X-Organization-Id")
            @Positive(message = "Organization ID must be positive")
            Long organizationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quotationService.createQuotation(
                request,
                jwt.getSubject(),
                organizationId
        );
    }

    // Get quotations for a procurement request
    @GetMapping("/procurement-request/{procurementRequestId}")
    public List<Quotation> getQuotationsByProcurementRequest(
            @PathVariable @Positive Long procurementRequestId,
            @RequestHeader("X-Organization-Id")
            @Positive(message = "Organization ID must be positive")
            Long organizationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quotationService.getQuotationsByProcurementRequest(
                procurementRequestId,
                jwt.getSubject(),
                organizationId
        );
    }

    // Get a quotation by ID
    @GetMapping("/{id}")
    public Quotation getQuotationById(
            @PathVariable @Positive Long id,
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
            @PathVariable @Positive Long id,
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
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quotationService.rejectQuotation(
                id,
                jwt.getSubject()
        );
    }
}