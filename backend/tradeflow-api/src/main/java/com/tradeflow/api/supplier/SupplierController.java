package com.tradeflow.api.supplier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
@Validated
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Supplier createSupplier(
            @Valid @RequestBody SupplierRequest request,
            @RequestHeader("X-Organization-Id") @Positive Long organizationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return supplierService.createSupplier(
                request,
                jwt.getSubject(),
                organizationId
        );
    }

    @GetMapping("/organization/{organizationId}")
    public List<Supplier> getSuppliersByOrganization(
            @PathVariable @Positive Long organizationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return supplierService.getSuppliersByOrganization(
                organizationId,
                jwt.getSubject()
        );
    }

    @GetMapping("/{id}")
    public Supplier getSupplierById(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return supplierService.getSupplierById(
                id,
                jwt.getSubject()
        );
    }
}
