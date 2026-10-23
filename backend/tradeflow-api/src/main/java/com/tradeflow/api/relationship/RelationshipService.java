package com.tradeflow.api.relationship;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationRepository;
import com.tradeflow.api.supplier.Supplier;
import com.tradeflow.api.supplier.SupplierRepository;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class RelationshipService {

    private final BuyerSupplierRelationshipRepository relationshipRepository;
    private final OrganizationRepository organizationRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;

    public RelationshipService(
            BuyerSupplierRelationshipRepository relationshipRepository,
            OrganizationRepository organizationRepository,
            SupplierRepository supplierRepository,
            UserRepository userRepository
    ) {
        this.relationshipRepository = relationshipRepository;
        this.organizationRepository = organizationRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
    }

    /**
     * A buyer can request a relationship with a supplier.
     * The buyer organization is taken from the authenticated user,
     * never trusted from the request body.
     */
    @Transactional
    public BuyerSupplierRelationship createRelationship(
            RelationshipRequest request,
            String authenticatedEmail
    ) {

        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.BUYER);

        Organization buyer = requireOrganization(user);

        Supplier supplier = supplierRepository
                .findById(request.supplierId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found"
                ));

        if (supplier.getOrganization() == null
                || supplier.getOrganization().getId().equals(buyer.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot create a buyer relationship with your own organization"
            );
        }

        if (relationshipRepository
                .existsByBuyerOrganization_IdAndSupplier_Id(
                        buyer.getId(),
                        supplier.getId()
                )) {

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

    /**
     * Buyers can view their own organization's relationships.
     * Administrators can view relationships for any buyer organization.
     */
    public List<BuyerSupplierRelationship> getBuyerRelationships(
            Long buyerOrganizationId,
            String authenticatedEmail
    ) {

        User user = getAuthenticatedUser(authenticatedEmail);

        Organization requestedOrganization = organizationRepository
                .findById(buyerOrganizationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Buyer organization not found"
                ));

        if (user.getRole() != UserRole.ADMIN) {
            Organization ownOrganization = requireOrganization(user);

            if (user.getRole() != UserRole.BUYER
                    || !ownOrganization.getId()
                    .equals(requestedOrganization.getId())) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not authorized to view these relationships"
                );
            }
        }

        return relationshipRepository
                .findAllByBuyerOrganization_Id(buyerOrganizationId);
    }

    /**
     * A supplier can view relationships involving its own supplier profile.
     * Administrators can view relationships for any supplier profile.
     */
    public List<BuyerSupplierRelationship> getSupplierRelationships(
            Long supplierId,
            String authenticatedEmail
    ) {

        User user = getAuthenticatedUser(authenticatedEmail);

        Supplier supplier = supplierRepository
                .findById(supplierId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found"
                ));

        if (user.getRole() != UserRole.ADMIN) {
            Organization ownOrganization = requireOrganization(user);

            if (user.getRole() != UserRole.SUPPLIER
                    || supplier.getOrganization() == null
                    || !ownOrganization.getId()
                    .equals(supplier.getOrganization().getId())) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not authorized to view these relationships"
                );
            }
        }

        return relationshipRepository.findAllBySupplier_Id(supplierId);
    }

    /**
     * A supplier can approve a pending relationship request.
     * Administrators can also approve it.
     */
    @Transactional
    public BuyerSupplierRelationship approveRelationship(
            Long id,
            String authenticatedEmail
    ) {

        User user = getAuthenticatedUser(authenticatedEmail);

        BuyerSupplierRelationship relationship = getRelationship(id);

        verifySupplierOrAdminAccess(user, relationship);

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
     * A supplier can reject a pending relationship request.
     * Administrators can also reject it.
     */
    @Transactional
    public BuyerSupplierRelationship rejectRelationship(
            Long id,
            String authenticatedEmail
    ) {

        User user = getAuthenticatedUser(authenticatedEmail);

        BuyerSupplierRelationship relationship = getRelationship(id);

        verifySupplierOrAdminAccess(user, relationship);

        if (relationship.getStatus() != RelationshipStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending relationships can be rejected"
            );
        }

        relationship.setStatus(RelationshipStatus.REJECTED);

        return relationshipRepository.save(relationship);
    }

    private BuyerSupplierRelationship getRelationship(Long id) {

        return relationshipRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Buyer-supplier relationship not found"
                ));
    }

    private void verifySupplierOrAdminAccess(
            User user,
            BuyerSupplierRelationship relationship
    ) {

        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        if (user.getRole() != UserRole.SUPPLIER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the associated supplier or an administrator can change relationship status"
            );
        }

        Organization organization = requireOrganization(user);

        if (relationship.getSupplier().getOrganization() == null
                || !organization.getId().equals(
                relationship.getSupplier().getOrganization().getId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot change a relationship belonging to another supplier"
            );
        }
    }

    private User getAuthenticatedUser(String authenticatedEmail) {

        String normalizedEmail = authenticatedEmail
                .trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found"
                ));

        if (!user.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is disabled"
            );
        }

        return user;
    }

    private Organization requireOrganization(User user) {

        Organization organization = user.getOrganization();

        if (organization == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is not associated with an organization"
            );
        }

        return organization;
    }

    private void requireRole(User user, UserRole expectedRole) {

        if (user.getRole() != expectedRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account does not have permission to perform this operation"
            );
        }
    }
}