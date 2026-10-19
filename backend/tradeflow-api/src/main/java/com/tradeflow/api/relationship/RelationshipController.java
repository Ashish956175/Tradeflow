package com.tradeflow.api.relationship;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/relationships")
public class RelationshipController {

    private final RelationshipService relationshipService;

    public RelationshipController(
            RelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BuyerSupplierRelationship createRelationship(
            @Valid @RequestBody RelationshipRequest request) {
        return relationshipService.createRelationship(request);
    }

    @GetMapping("/buyer/{organizationId}")
    public List<BuyerSupplierRelationship> getBuyerRelationships(
            @PathVariable Long organizationId) {
        return relationshipService.getBuyerRelationships(organizationId);
    }

    @GetMapping("/supplier/{supplierId}")
    public List<BuyerSupplierRelationship> getSupplierRelationships(
            @PathVariable Long supplierId) {
        return relationshipService.getSupplierRelationships(supplierId);
    }
}