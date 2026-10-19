package com.tradeflow.api.procurement;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
            @Valid @RequestBody ProcurementRequestRequest request
    ) {
        return procurementRequestService.createProcurementRequest(request);
    }

    @GetMapping
    public List<ProcurementRequest> getAllProcurementRequests() {
        return procurementRequestService.getAllProcurementRequests();
    }

    @GetMapping("/{id}")
    public ProcurementRequest getProcurementRequestById(
            @PathVariable Long id
    ) {
        return procurementRequestService.getProcurementRequestById(id);
    }

    @GetMapping("/buyer/{organizationId}")
    public List<ProcurementRequest> getProcurementRequestsByBuyer(
            @PathVariable Long organizationId
    ) {
        return procurementRequestService
                .getProcurementRequestsByBuyer(organizationId);
    }
}