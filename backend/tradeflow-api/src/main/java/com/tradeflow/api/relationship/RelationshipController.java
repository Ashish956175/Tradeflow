package com.tradeflow.api.relationship;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/relationships")
@Validated
public class RelationshipController {

    private final RelationshipService relationshipService;

    public RelationshipController(
            RelationshipService relationshipService
    ) {
        this.relationshipService = relationshipService;
    }

    /**
     * Create a buyer-supplier relationship request.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BuyerSupplierRelationship createRelationship(
            @Valid @RequestBody RelationshipRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relationshipService.createRelationship(
                request,
                jwt.getSubject()
        );
    }

    /**
     * Retrieve relationships associated with a buyer organization.
     */
    @GetMapping("/buyer/{organizationId}")
    public List<BuyerSupplierRelationship> getBuyerRelationships(
            @PathVariable @Positive Long organizationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relationshipService.getBuyerRelationships(
                organizationId,
                jwt.getSubject()
        );
    }

    /**
     * Retrieve relationships associated with a supplier profile.
     */
    @GetMapping("/supplier/{supplierId}")
    public List<BuyerSupplierRelationship> getSupplierRelationships(
            @PathVariable @Positive Long supplierId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relationshipService.getSupplierRelationships(
                supplierId,
                jwt.getSubject()
        );
    }

    /**
     * Approve a pending relationship request.
     */
    @PatchMapping("/{id}/approve")
    public BuyerSupplierRelationship approveRelationship(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relationshipService.approveRelationship(
                id,
                jwt.getSubject()
        );
    }

    /**
     * Reject a pending relationship request.
     */
    @PatchMapping("/{id}/reject")
    public BuyerSupplierRelationship rejectRelationship(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relationshipService.rejectRelationship(
                id,
                jwt.getSubject()
        );
    }
}