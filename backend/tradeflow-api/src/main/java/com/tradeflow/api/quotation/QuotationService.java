package com.tradeflow.api.quotation;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.procurement.ProcurementRequestRepository;
import com.tradeflow.api.procurement.ProcurementStatus;
import com.tradeflow.api.relationship.BuyerSupplierRelationship;
import com.tradeflow.api.relationship.BuyerSupplierRelationshipRepository;
import com.tradeflow.api.relationship.RelationshipStatus;
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
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final SupplierRepository supplierRepository;
    private final BuyerSupplierRelationshipRepository relationshipRepository;
    private final UserRepository userRepository;

    public QuotationService(
            QuotationRepository quotationRepository,
            ProcurementRequestRepository procurementRequestRepository,
            SupplierRepository supplierRepository,
            BuyerSupplierRelationshipRepository relationshipRepository,
            UserRepository userRepository
    ) {
        this.quotationRepository = quotationRepository;
        this.procurementRequestRepository = procurementRequestRepository;
        this.supplierRepository = supplierRepository;
        this.relationshipRepository = relationshipRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Quotation createQuotation(
            QuotationRequest request,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.SUPPLIER);

        Organization organization = requireOrganization(user);

        Supplier supplier = findSupplierForOrganization(organization);

        ProcurementRequest procurementRequest =
                procurementRequestRepository.findById(
                        request.procurementRequestId()
                ).orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Procurement request not found"
                ));

        if (procurementRequest.getStatus() != ProcurementStatus.OPEN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Quotations can only be submitted for open procurement requests"
            );
        }

        Long buyerOrganizationId =
                procurementRequest.getBuyerOrganization().getId();

        BuyerSupplierRelationship relationship =
                relationshipRepository
                        .findByBuyerOrganization_IdAndSupplier_Id(
                                buyerOrganizationId,
                                supplier.getId()
                        )
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Supplier does not have a relationship with this buyer"
                        ));

        if (relationship.getStatus() != RelationshipStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Supplier relationship must be active to submit a quotation"
            );
        }

        boolean alreadySubmitted =
                quotationRepository
                        .existsByProcurementRequest_IdAndSupplier_Id(
                                procurementRequest.getId(),
                                supplier.getId()
                        );

        if (alreadySubmitted) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This supplier has already submitted a quotation for this request"
            );
        }

        Quotation quotation = new Quotation(
                procurementRequest,
                supplier,
                request.offeredUnitPrice(),
                request.offeredQuantity(),
                request.estimatedDeliveryDate()
        );

        return quotationRepository.save(quotation);
    }

    @Transactional(readOnly = true)
    public List<Quotation> getQuotationsByProcurementRequest(
            Long procurementRequestId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        ProcurementRequest procurementRequest =
                procurementRequestRepository.findById(procurementRequestId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Procurement request not found"
                        ));

        if (user.getRole() != UserRole.ADMIN) {
            Organization organization = requireOrganization(user);

            if (user.getRole() == UserRole.BUYER) {
                if (!organization.getId().equals(
                        procurementRequest.getBuyerOrganization().getId()
                )) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "You cannot view quotations for another buyer's request"
                    );
                }
            } else if (user.getRole() == UserRole.SUPPLIER) {
                Supplier supplier = findSupplierForOrganization(organization);

                boolean hasRelationship =
                        relationshipRepository
                                .findByBuyerOrganization_IdAndSupplier_Id(
                                        procurementRequest
                                                .getBuyerOrganization()
                                                .getId(),
                                        supplier.getId()
                                )
                                .filter(r -> r.getStatus() == RelationshipStatus.ACTIVE)
                                .isPresent();

                if (!hasRelationship) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "You are not authorized to view quotations for this request"
                    );
                }
            } else {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not authorized to view quotations"
                );
            }
        }

        return quotationRepository
                .findByProcurementRequest_Id(procurementRequestId);
    }

    @Transactional(readOnly = true)
    public Quotation getQuotationById(
            Long id,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        Quotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Quotation not found"
                ));

        authorizeQuotationRead(user, quotation);

        return quotation;
    }

    @Transactional
    public Quotation acceptQuotation(
            Long quotationId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.BUYER);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Quotation not found"
                ));

        verifyBuyerOwnsRequest(
                user,
                quotation.getProcurementRequest()
        );

        if (quotation.getStatus() != QuotationStatus.SUBMITTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only submitted quotations can be accepted"
            );
        }

        ProcurementRequest procurementRequest =
                quotation.getProcurementRequest();

        try {
            procurementRequest.acceptQuotation();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        quotation.accept();

        return quotationRepository.save(quotation);
    }

    @Transactional
    public Quotation rejectQuotation(
            Long quotationId,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);
        requireRole(user, UserRole.BUYER);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Quotation not found"
                ));

        verifyBuyerOwnsRequest(
                user,
                quotation.getProcurementRequest()
        );

        if (quotation.getStatus() != QuotationStatus.SUBMITTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only submitted quotations can be rejected"
            );
        }

        quotation.reject();

        return quotationRepository.save(quotation);
    }

    private User getAuthenticatedUser(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        if (!user.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is disabled"
            );
        }

        return user;
    }

    private void requireRole(User user, UserRole requiredRole) {
        if (user.getRole() != requiredRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account does not have permission to perform this action"
            );
        }
    }

    private Organization requireOrganization(User user) {
        Organization organization = user.getOrganization();

        if (organization == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account must be associated with an organization"
            );
        }

        return organization;
    }

    private Supplier findSupplierForOrganization(
            Organization organization
    ) {
        List<Supplier> suppliers =
                supplierRepository.findAllByOrganization_Id(
                        organization.getId()
                );

        if (suppliers.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No supplier profile is registered for your organization"
            );
        }

        if (suppliers.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Multiple supplier profiles exist for your organization; supplier account mapping must be configured"
            );
        }

        return suppliers.get(0);
    }

    private void verifyBuyerOwnsRequest(
            User user,
            ProcurementRequest procurementRequest
    ) {
        Organization organization = requireOrganization(user);

        if (!organization.getId().equals(
                procurementRequest.getBuyerOrganization().getId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot manage quotations for another buyer's request"
            );
        }
    }

    private void authorizeQuotationRead(
            User user,
            Quotation quotation
    ) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        Organization organization = requireOrganization(user);

        if (user.getRole() == UserRole.BUYER) {
            verifyBuyerOwnsRequest(
                    user,
                    quotation.getProcurementRequest()
            );
            return;
        }

        if (user.getRole() == UserRole.SUPPLIER) {
            Supplier supplier = findSupplierForOrganization(organization);

            if (!supplier.getId().equals(
                    quotation.getSupplier().getId()
            )) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You cannot view another supplier's quotation"
                );
            }

            return;
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not authorized to view this quotation"
        );
    }
}