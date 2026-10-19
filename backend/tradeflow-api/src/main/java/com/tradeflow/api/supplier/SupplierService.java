package com.tradeflow.api.supplier;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final OrganizationRepository organizationRepository;

    public SupplierService(
            SupplierRepository supplierRepository,
            OrganizationRepository organizationRepository) {
        this.supplierRepository = supplierRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public Supplier createSupplier(SupplierRequest request) {

        Long organizationId = request.organizationId();

        Organization organization = organizationRepository
                .findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organization not found"
                ));

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase();

        if (supplierRepository.existsByOrganization_IdAndEmail(
                organizationId, normalizedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A supplier with this email already exists in this organization"
            );
        }

        Supplier supplier = new Supplier();
        supplier.setOrganization(organization);
        supplier.setName(request.name().trim());
        supplier.setEmail(normalizedEmail);
        supplier.setPhone(normalizeOptional(request.phone()));
        supplier.setAddress(normalizeOptional(request.address()));

        return supplierRepository.save(supplier);
    }

    public List<Supplier> getSuppliersByOrganization(Long organizationId) {

        if (!organizationRepository.existsById(organizationId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Organization not found"
            );
        }

        return supplierRepository.findAllByOrganization_Id(organizationId);
    }

    public Supplier getSupplierById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found"
                ));
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}