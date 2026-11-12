package com.tradeflow.api.quotation;

import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.procurement.ProcurementRequestRepository;
import com.tradeflow.api.procurement.ProcurementStatus;
import com.tradeflow.api.relationship.BuyerSupplierRelationship;
import com.tradeflow.api.relationship.BuyerSupplierRelationshipRepository;
import com.tradeflow.api.relationship.RelationshipStatus;
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
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final ProcurementRequestRepository procurementRequestRepository;
    private final SupplierRepository supplierRepository;
    private final BuyerSupplierRelationshipRepository relationshipRepository;
    private final MembershipAuthorizationService authorizationService;

    public QuotationService(
            QuotationRepository quotationRepository,
            ProcurementRequestRepository procurementRequestRepository,
            SupplierRepository supplierRepository,
            BuyerSupplierRelationshipRepository relationshipRepository,
            MembershipAuthorizationService authorizationService
    ) {
        this.quotationRepository = quotationRepository;
        this.procurementRequestRepository = procurementRequestRepository;
        this.supplierRepository = supplierRepository;
        this.relationshipRepository = relationshipRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public Quotation createQuotation(
            QuotationRequest request,
            String authenticatedEmail,
            Long organizationId
    ) {
        User user = authorizationService.getAuthenticatedUser(authenticatedEmail);

        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Platform administrators cannot submit quotations"
            );
        }

        authorizationService.requireOrganizationCapability(
                authenticatedEmail,
                organizationId,
                OrganizationCapability.SUPPLIER
        );

        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier profile not found"
                ));

        if (!supplier.getOrganization().getId().equals(organizationId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The selected supplier does not belong to your organization"
            );
        }

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
            String authenticatedEmail,
            Long organizationId
    ) {
        User user = authorizationService.getAuthenticatedUser(authenticatedEmail);

        ProcurementRequest procurementRequest =
                procurementRequestRepository.findById(procurementRequestId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Procurement request not found"
                        ));

        if (user.getRole() == UserRole.ADMIN) {
            return quotationRepository
                    .findByProcurementRequest_Id(procurementRequestId);
        }

        Long buyerOrganizationId =
                procurementRequest.getBuyerOrganization().getId();

        if (buyerOrganizationId.equals(organizationId)) {

            authorizationService.requireOrganizationCapability(
                    authenticatedEmail,
                    organizationId,
                    OrganizationCapability.BUYER
            );

            return quotationRepository
                    .findByProcurementRequest_Id(procurementRequestId);
        }

        authorizationService.requireOrganizationCapability(
                authenticatedEmail,
                organizationId,
                OrganizationCapability.SUPPLIER
        );

        List<Supplier> suppliers =
                supplierRepository.findAllByOrganization_Id(organizationId);

        if (suppliers.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "No supplier profiles are registered for your organization"
            );
        }

        boolean hasActiveRelationship = suppliers.stream()
                .map(Supplier::getId)
                .map(supplierId ->
                        relationshipRepository
                                .findByBuyerOrganization_IdAndSupplier_Id(
                                        buyerOrganizationId,
                                        supplierId
                                )
                )
                .flatMap(java.util.Optional::stream)
                .anyMatch(relationship ->
                        relationship.getStatus() == RelationshipStatus.ACTIVE
                );

        if (!hasActiveRelationship) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your organization does not have an active relationship with this buyer"
            );
        }

        return quotationRepository
                .findByProcurementRequest_Id(procurementRequestId)
                .stream()
                .filter(quotation ->
                        quotation.getSupplier()
                                .getOrganization()
                                .getId()
                                .equals(organizationId)
                )
                .toList();
    }

    @Transactional(readOnly = true)
    public Quotation getQuotationById(
            Long id,
            String authenticatedEmail
    ) {
        User user = authorizationService.getAuthenticatedUser(authenticatedEmail);

        Quotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Quotation not found"
                ));

        if (user.getRole() == UserRole.ADMIN) {
            return quotation;
        }

        Long buyerOrganizationId =
                quotation.getProcurementRequest()
                        .getBuyerOrganization()
                        .getId();

        Long supplierOrganizationId =
                quotation.getSupplier()
                        .getOrganization()
                        .getId();

        try {
            authorizationService.requireOrganizationCapability(
                    authenticatedEmail,
                    buyerOrganizationId,
                    OrganizationCapability.BUYER
            );

            return quotation;

        } catch (ResponseStatusException buyerAccessDenied) {

            if (buyerAccessDenied.getStatusCode() != HttpStatus.FORBIDDEN) {
                throw buyerAccessDenied;
            }
        }

        authorizationService.requireOrganizationCapability(
                authenticatedEmail,
                supplierOrganizationId,
                OrganizationCapability.SUPPLIER
        );

        return quotation;
    }

    @Transactional
    public Quotation acceptQuotation(
            Long quotationId,
            String authenticatedEmail
    ) {
        User user = authorizationService.getAuthenticatedUser(authenticatedEmail);

        rejectPlatformAdmin(user);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Quotation not found"
                ));

        requireBuyerCapability(
                authenticatedEmail,
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
        User user = authorizationService.getAuthenticatedUser(authenticatedEmail);

        rejectPlatformAdmin(user);

        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Quotation not found"
                ));

        requireBuyerCapability(
                authenticatedEmail,
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

    private void requireBuyerCapability(
            String authenticatedEmail,
            ProcurementRequest procurementRequest
    ) {
        Long buyerOrganizationId =
                procurementRequest.getBuyerOrganization().getId();

        authorizationService.requireOrganizationCapability(
                authenticatedEmail,
                buyerOrganizationId,
                OrganizationCapability.BUYER
        );
    }

    private void rejectPlatformAdmin(User user) {
        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Platform administrators cannot manage quotations"
            );
        }
    }

}