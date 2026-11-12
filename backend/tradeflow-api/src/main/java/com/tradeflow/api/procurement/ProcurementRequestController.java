package com.tradeflow.api.procurement;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/procurement-requests")
public class ProcurementRequestController {

    private final ProcurementRequestService procurementRequestService;

    public ProcurementRequestController(
            ProcurementRequestService procurementRequestService
    ) {
        this.procurementRequestService = procurementRequestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProcurementRequest createProcurementRequest(
            @Valid @RequestBody ProcurementRequestRequest request,
            @RequestHeader("X-Organization-Id") Long organizationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return procurementRequestService.createProcurementRequest(
                request,
                jwt.getSubject(),
                organizationId
        );
    }

    @GetMapping
    public List<ProcurementRequest> getMyProcurementRequests(
            @RequestHeader("X-Organization-Id") Long organizationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return procurementRequestService.getMyProcurementRequests(
                jwt.getSubject(),
                organizationId
        );
    }

    @GetMapping("/{id}")
    public ProcurementRequest getProcurementRequestById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return procurementRequestService.getProcurementRequestById(
                id,
                jwt.getSubject()
        );
    }
}
