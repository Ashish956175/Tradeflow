package com.tradeflow.api.relationship;

import com.tradeflow.api.membership.OrganizationMembership;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.organization.OrganizationCapabilityRepository;
import com.tradeflow.api.organization.OrganizationRepository;
import com.tradeflow.api.security.MembershipAuthorizationService;
import com.tradeflow.api.supplier.Supplier;
import com.tradeflow.api.supplier.SupplierRepository;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRole;

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
    private final OrganizationCapabilityRepository organizationCapabilityRepository;
    private final MembershipAuthorizationService authorizationService;

    public RelationshipService(
            BuyerSupplierRelationshipRepository relationshipRepository,
            OrganizationRepository organizationRepository,
            SupplierRepository supplierRepository,
            OrganizationCapabilityRepository organizationCapabilityRepository,
            MembershipAuthorizationService authorizationService
    ) {
        this.relationshipRepository = relationshipRepository;
        this.organizationRepository = organizationRepository;
        this.supplierRepository = supplierRepository;
        this.organizationCapabilityRepository = organizationCapabilityRepository;
        this.authorizationService = authorizationService;
    }

    /**
     * A buyer member can request a relationship with a supplier.
     * The buyer organization comes from the active membership,
     * not from the request body.
     */
    @Transactional
    public BuyerSupplierRelationship createRelationship(
            RelationshipRequest request,
            String authenticatedEmail,
            Long organizationId
    ) {

        User user = authorizationService.getAuthenticatedUser(
                authenticatedEmail
        );

        rejectPlatformAdminWrite(user);

        OrganizationMembership membership =
                authorizationService.requireOrganizationCapability(
                        authenticatedEmail,
                        organizationId,
                        OrganizationCapability.BUYER
                );

        Organization buyerOrganization = membership.getOrganization();

        Supplier supplier = supplierRepository
                .findById(request.supplierId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found"
                ));

        Organization supplierOrganization = supplier.getOrganization();

        if (supplierOrganization == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Supplier is not associated with an organization"
            );
        }

        if (buyerOrganization.getId().equals(
                supplierOrganization.getId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot create a buyer relationship with your own organization"
            );
        }

        boolean supplierCapabilityEnabled =
                organizationCapabilityRepository
                        .existsByOrganizationIdAndCapability(
                                supplierOrganization.getId(),
                                OrganizationCapability.SUPPLIER
                        );

        if (!supplierCapabilityEnabled) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The supplier organization does not have SUPPLIER capability enabled"
            );
        }

        if (relationshipRepository
                .existsByBuyerOrganization_IdAndSupplier_Id(
                        buyerOrganization.getId(),
                        supplier.getId()
                )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This buyer-supplier relationship already exists"
            );
        }

        BuyerSupplierRelationship relationship =
                new BuyerSupplierRelationship();

        relationship.setBuyerOrganization(buyerOrganization);
        relationship.setSupplier(supplier);
        relationship.setStatus(RelationshipStatus.PENDING);

        return relationshipRepository.save(relationship);
    }

    /**
     * Platform administrators can view all buyer relationships.
     * Other users need BUYER capability in the requested organization.
     */
    public List<BuyerSupplierRelationship> getBuyerRelationships(
            Long buyerOrganizationId,
            String authenticatedEmail
    ) {

        User user = authorizationService.getAuthenticatedUser(
                authenticatedEmail
        );

        Organization requestedOrganization = organizationRepository
                .findById(buyerOrganizationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Buyer organization not found"
                ));

        if (!isPlatformAdmin(user)) {
            authorizationService.requireOrganizationCapability(
                    authenticatedEmail,
                    requestedOrganization.getId(),
                    OrganizationCapability.BUYER
            );
        }

        return relationshipRepository
                .findAllByBuyerOrganization_Id(buyerOrganizationId);
    }

    /**
     * Platform administrators can view all supplier relationships.
     * Other users need SUPPLIER capability in the supplier's organization.
     */
    public List<BuyerSupplierRelationship> getSupplierRelationships(
            Long supplierId,
            String authenticatedEmail
    ) {

        User user = authorizationService.getAuthenticatedUser(
                authenticatedEmail
        );

        Supplier supplier = supplierRepository
                .findById(supplierId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found"
                ));

        if (!isPlatformAdmin(user)) {
            Organization supplierOrganization =
                    supplier.getOrganization();

            if (supplierOrganization == null) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Supplier is not associated with an organization"
                );
            }

            authorizationService.requireOrganizationCapability(
                    authenticatedEmail,
                    supplierOrganization.getId(),
                    OrganizationCapability.SUPPLIER
            );
        }

        return relationshipRepository.findAllBySupplier_Id(supplierId);
    }

    /**
     * Only authorized supplier organization members can approve.
     * Platform administrators have read-only access.
     */
    @Transactional
    public BuyerSupplierRelationship approveRelationship(
            Long id,
            String authenticatedEmail
    ) {

        User user = authorizationService.getAuthenticatedUser(
                authenticatedEmail
        );

        rejectPlatformAdminWrite(user);

        BuyerSupplierRelationship relationship = getRelationship(id);

        requireSupplierCapability(authenticatedEmail, relationship);

        if (relationship.getStatus() != RelationshipStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending relationships can be approved"
            );
        }

        relationship.setStatus(RelationshipStatus.ACTIVE);

        return relationshipRepository.save(relationship);
    }

    /**
     * Only authorized supplier organization members can reject.
     * Platform administrators have read-only access.
     */
    @Transactional
    public BuyerSupplierRelationship rejectRelationship(
            Long id,
            String authenticatedEmail
    ) {

        User user = authorizationService.getAuthenticatedUser(
                authenticatedEmail
        );

        rejectPlatformAdminWrite(user);

        BuyerSupplierRelationship relationship = getRelationship(id);

        requireSupplierCapability(authenticatedEmail, relationship);

        if (relationship.getStatus() != RelationshipStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending relationships can be rejected"
            );
        }

        relationship.setStatus(RelationshipStatus.REJECTED);

        return relationshipRepository.save(relationship);
    }

    private void requireSupplierCapability(
            String authenticatedEmail,
            BuyerSupplierRelationship relationship
    ) {

        Supplier supplier = relationship.getSupplier();

        if (supplier == null || supplier.getOrganization() == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The supplier is not associated with an organization"
            );
        }

        authorizationService.requireOrganizationCapability(
                authenticatedEmail,
                supplier.getOrganization().getId(),
                OrganizationCapability.SUPPLIER
        );
    }

    private BuyerSupplierRelationship getRelationship(Long id) {

        return relationshipRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Buyer-supplier relationship not found"
                ));
    }

    private boolean isPlatformAdmin(User user) {
        return user.getRole() == UserRole.ADMIN;
    }

    private void rejectPlatformAdminWrite(User user) {

        if (isPlatformAdmin(user)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Platform administrators have read-only access to relationships"
            );
        }
    }
}