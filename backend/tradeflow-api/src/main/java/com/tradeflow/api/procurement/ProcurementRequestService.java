package com.tradeflow.api.procurement;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.product.Product;
import com.tradeflow.api.product.ProductRepository;
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
public class ProcurementRequestService {

    private final ProcurementRequestRepository procurementRequestRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProcurementRequestService(
            ProcurementRequestRepository procurementRequestRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.procurementRequestRepository = procurementRequestRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProcurementRequest createProcurementRequest(
            ProcurementRequestRequest request,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        if (user.getRole() != UserRole.BUYER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only buyers can create procurement requests"
            );
        }

        Organization buyerOrganization = user.getOrganization();

        if (buyerOrganization == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Your account must be associated with an organization first"
            );
        }

        Product product = productRepository
                .findById(request.productId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        ProcurementRequest procurementRequest = new ProcurementRequest(
                buyerOrganization,
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
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        if (user.getRole() != UserRole.BUYER) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only buyers can access their procurement requests"
            );
        }

        Organization organization = user.getOrganization();

        if (organization == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Your account must be associated with an organization first"
            );
        }

        return procurementRequestRepository
                .findByBuyerOrganization_Id(organization.getId());
    }

    @Transactional(readOnly = true)
    public ProcurementRequest getProcurementRequestById(
            Long id,
            String authenticatedEmail
    ) {
        User user = getAuthenticatedUser(authenticatedEmail);

        ProcurementRequest request = procurementRequestRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Procurement request not found"
                ));

        if (user.getRole() == UserRole.ADMIN) {
            return request;
        }

        Organization organization = user.getOrganization();

        if (organization == null
                || !organization.getId().equals(
                request.getBuyerOrganization().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to access this procurement request"
            );
        }

        return request;
    }

    private User getAuthenticatedUser(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

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
}