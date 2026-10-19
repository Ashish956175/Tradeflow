package com.tradeflow.api.relationship;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationRepository;
import com.tradeflow.api.supplier.Supplier;
import com.tradeflow.api.supplier.SupplierRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RelationshipService {

    private final BuyerSupplierRelationshipRepository relationshipRepository;
    private final OrganizationRepository organizationRepository;
    private final SupplierRepository supplierRepository;

    public RelationshipService(
            BuyerSupplierRelationshipRepository relationshipRepository,
            OrganizationRepository organizationRepository,
            SupplierRepository supplierRepository) {
        this.relationshipRepository = relationshipRepository;
        this.organizationRepository = organizationRepository;
        this.supplierRepository = supplierRepository;
    }

    @Transactional
    public BuyerSupplierRelationship createRelationship(
            RelationshipRequest request) {

        Organization buyer = organizationRepository
                .findById(request.buyerOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Buyer organization not found"
                ));

        Supplier supplier = supplierRepository
                .findById(request.supplierId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found"
                ));

        // A business cannot establish a buyer relationship with
        // a supplier profile belonging to the same organization.
        if (supplier.getOrganization().getId().equals(buyer.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "An organization cannot create a buyer relationship with itself"
            );
        }

        if (relationshipRepository
                .existsByBuyerOrganization_IdAndSupplier_Id(
                        buyer.getId(),
                        supplier.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This buyer-supplier relationship already exists"
            );
        }

        BuyerSupplierRelationship relationship =
                new BuyerSupplierRelationship();

        relationship.setBuyerOrganization(buyer);
        relationship.setSupplier(supplier);
        relationship.setStatus(RelationshipStatus.PENDING);

        return relationshipRepository.save(relationship);
    }

    public List<BuyerSupplierRelationship> getBuyerRelationships(
            Long buyerOrganizationId) {

        if (!organizationRepository.existsById(buyerOrganizationId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Buyer organization not found"
            );
        }

        return relationshipRepository
                .findAllByBuyerOrganization_Id(buyerOrganizationId);
    }

    public List<BuyerSupplierRelationship> getSupplierRelationships(
            Long supplierId) {

        if (!supplierRepository.existsById(supplierId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Supplier not found"
            );
        }

        return relationshipRepository.findAllBySupplier_Id(supplierId);
    }

    @Transactional
    public BuyerSupplierRelationship approveRelationship(Long id) {

        BuyerSupplierRelationship relationship = relationshipRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Buyer-supplier relationship not found"
                ));

        if (relationship.getStatus() != RelationshipStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending relationships can be approved"
            );
        }

        relationship.setStatus(RelationshipStatus.ACTIVE);

        return relationshipRepository.save(relationship);
    }

    @Transactional
    public BuyerSupplierRelationship rejectRelationship(Long id) {

        BuyerSupplierRelationship relationship = relationshipRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Buyer-supplier relationship not found"
                ));

        if (relationship.getStatus() != RelationshipStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending relationships can be rejected"
            );
        }

        relationship.setStatus(RelationshipStatus.REJECTED);

        return relationshipRepository.save(relationship);
    }

}