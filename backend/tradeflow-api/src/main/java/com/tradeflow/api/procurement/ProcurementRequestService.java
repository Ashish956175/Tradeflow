package com.tradeflow.api.procurement;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationRepository;
import com.tradeflow.api.product.Product;
import com.tradeflow.api.product.ProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProcurementRequestService {

    private final ProcurementRequestRepository procurementRequestRepository;
    private final OrganizationRepository organizationRepository;
    private final ProductRepository productRepository;

    public ProcurementRequestService(
            ProcurementRequestRepository procurementRequestRepository,
            OrganizationRepository organizationRepository,
            ProductRepository productRepository
    ) {
        this.procurementRequestRepository = procurementRequestRepository;
        this.organizationRepository = organizationRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public ProcurementRequest createProcurementRequest(
            ProcurementRequestRequest request
    ) {
        Organization buyerOrganization = organizationRepository
                .findById(request.buyerOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Buyer organization not found"
                ));

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
    public List<ProcurementRequest> getAllProcurementRequests() {
        return procurementRequestRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ProcurementRequest getProcurementRequestById(Long id) {
        return procurementRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Procurement request not found"
                ));
    }

    @Transactional(readOnly = true)
    public List<ProcurementRequest> getProcurementRequestsByBuyer(
            Long buyerOrganizationId
    ) {
        if (!organizationRepository.existsById(buyerOrganizationId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Buyer organization not found"
            );
        }

        return procurementRequestRepository
                .findByBuyerOrganization_Id(buyerOrganizationId);
    }
}