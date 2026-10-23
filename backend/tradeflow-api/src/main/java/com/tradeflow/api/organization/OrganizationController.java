package com.tradeflow.api.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@Validated
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(
            OrganizationService organizationService
    ) {
        this.organizationService = organizationService;
    }

    /**
     * Creates an organization through the administrator-only endpoint.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Organization createOrganization(
            @Valid @RequestBody OrganizationRequest request
    ) {
        return organizationService.createOrganization(request);
    }

    /**
     * Allows authenticated buyers and suppliers to onboard their organization.
     */
    @PostMapping("/onboard")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationOnboardResponse onboardOrganization(
            @Valid @RequestBody OrganizationOnboardRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return organizationService.onboardOrganization(
                request,
                jwt.getSubject()
        );
    }

    /**
     * Only administrators can retrieve every organization.
     */
    @GetMapping
    public List<Organization> getAllOrganizations(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return organizationService.getAllOrganizations(
                jwt.getSubject()
        );
    }

    /**
     * Administrators can retrieve any organization.
     * Other users can retrieve only their own organization.
     */
    @GetMapping("/{id}")
    public Organization getOrganizationById(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return organizationService.getOrganizationById(
                id,
                jwt.getSubject()
        );
    }
}