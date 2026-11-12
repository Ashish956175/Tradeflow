package com.tradeflow.api.supplier;

import com.tradeflow.api.membership.OrganizationMembership;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.organization.OrganizationRepository;
import com.tradeflow.api.security.MembershipAuthorizationService;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRole;

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
    private final MembershipAuthorizationService membershipAuthorizationService;

    public SupplierService(
            SupplierRepository supplierRepository,
            OrganizationRepository organizationRepository,
            MembershipAuthorizationService membershipAuthorizationService
    ) {
        this.supplierRepository = supplierRepository;
        this.organizationRepository = organizationRepository;
        this.membershipAuthorizationService = membershipAuthorizationService;
    }

    @Transactional
    public Supplier createSupplier(
            SupplierRequest request,
            String email,
            Long organizationId
    ) {

        User user = membershipAuthorizationService.getAuthenticatedUser(email);

        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Platform administrators cannot create supplier profiles"
            );
        }

        OrganizationMembership membership =
                membershipAuthorizationService.requireOrganizationCapability(
                        email,
                        organizationId,
                        OrganizationCapability.SUPPLIER
                );

        Organization organization = membership.getOrganization();

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase();

        if (supplierRepository.existsByOrganization_IdAndEmail(
                organization.getId(),
                normalizedEmail)) {
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

    public List<Supplier> getSuppliersByOrganization(
            Long organizationId,
            String email
    ) {

        User user = membershipAuthorizationService.getAuthenticatedUser(email);

        if (user.getRole() != UserRole.ADMIN) {
            membershipAuthorizationService.requireOrganizationCapability(
                    email,
                    organizationId,
                    OrganizationCapability.SUPPLIER
            );
        }

        if (!organizationRepository.existsById(organizationId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Organization not found"
            );
        }

        return supplierRepository.findAllByOrganization_Id(organizationId);
    }

    public Supplier getSupplierById(Long id, String email) {

        User user = membershipAuthorizationService.getAuthenticatedUser(email);

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Supplier not found"
                ));

        if (user.getRole() != UserRole.ADMIN) {
            membershipAuthorizationService.requireOrganizationCapability(
                    email,
                    supplier.getOrganization().getId(),
                    OrganizationCapability.SUPPLIER
            );
        }

        return supplier;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
