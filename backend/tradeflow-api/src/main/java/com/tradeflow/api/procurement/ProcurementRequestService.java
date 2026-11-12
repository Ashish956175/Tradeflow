package com.tradeflow.api.procurement;

import com.tradeflow.api.membership.OrganizationMembership;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.product.Product;
import com.tradeflow.api.product.ProductRepository;
import com.tradeflow.api.security.MembershipAuthorizationService;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRole;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProcurementRequestService {

    private final ProcurementRequestRepository procurementRequestRepository;
    private final ProductRepository productRepository;
    private final MembershipAuthorizationService authorizationService;

    public ProcurementRequestService(
            ProcurementRequestRepository procurementRequestRepository,
            ProductRepository productRepository,
            MembershipAuthorizationService authorizationService
    ) {
        this.procurementRequestRepository = procurementRequestRepository;
        this.productRepository = productRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public ProcurementRequest createProcurementRequest(
            ProcurementRequestRequest request,
            String authenticatedEmail,
            Long organizationId
    ) {
        OrganizationMembership membership =
                authorizationService.requireOrganizationCapability(
                        authenticatedEmail,
                        organizationId,
                        OrganizationCapability.BUYER
                );

        Product product = productRepository
                .findById(request.productId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        ProcurementRequest procurementRequest = new ProcurementRequest(
                membership.getOrganization(),
                product,
                request.requestedQuantity(),
                request.targetUnitPrice(),
                request.deliveryAddress(),
                request.requiredDeliveryDate()
        );

        return procurementRequestRepository.save(procurementRequest);
    }

    @Transactional(readOnly = true)
    public List<ProcurementRequest> getMyProcurementRequests(
            String authenticatedEmail,
            Long organizationId
    ) {
        OrganizationMembership membership =
                authorizationService.requireOrganizationCapability(
                        authenticatedEmail,
                        organizationId,
                        OrganizationCapability.BUYER
                );

        return procurementRequestRepository
                .findByBuyerOrganization_Id(
                        membership.getOrganization().getId()
                );
    }

    @Transactional(readOnly = true)
    public ProcurementRequest getProcurementRequestById(
            Long id,
            String authenticatedEmail
    ) {
        User user = authorizationService.getAuthenticatedUser(
                authenticatedEmail
        );

        ProcurementRequest request = procurementRequestRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Procurement request not found"
                ));

        if (user.getRole() == UserRole.ADMIN) {
            return request;
        }

        authorizationService.requireOrganizationCapability(
                authenticatedEmail,
                request.getBuyerOrganization().getId(),
                OrganizationCapability.BUYER
        );

        return request;
    }
}
