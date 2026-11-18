package com.tradeflow.api.organization;

import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import com.tradeflow.api.membership.MembershipRole;
import com.tradeflow.api.membership.MembershipStatus;
import com.tradeflow.api.membership.OrganizationMembership;
import com.tradeflow.api.membership.OrganizationMembershipRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationCapabilityRepository organizationCapabilityRepository;
    private final OrganizationCapabilityAuditRepository organizationCapabilityAuditRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            OrganizationCapabilityRepository organizationCapabilityRepository,
            OrganizationCapabilityAuditRepository organizationCapabilityAuditRepository,
            OrganizationMembershipRepository organizationMembershipRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.organizationCapabilityRepository = organizationCapabilityRepository;
        this.organizationCapabilityAuditRepository = organizationCapabilityAuditRepository;
        this.organizationMembershipRepository = organizationMembershipRepository;
    }

    @Transactional
    public Organization createOrganization(OrganizationRequest request) {

        String normalizedEmail = normalizeEmail(request.businessEmail());

        checkBusinessEmail(normalizedEmail);

        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setBusinessEmail(normalizedEmail);
        organization.setPhone(normalizeOptional(request.phone()));
        organization.setAddress(normalizeOptional(request.address()));

        return organizationRepository.save(organization);
    }

    @Transactional
    public OrganizationOnboardResponse onboardOrganization(
            OrganizationOnboardRequest request,
            String authenticatedEmail
    ) {

        User user = getAuthenticatedUser(authenticatedEmail);

        if (user.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin accounts cannot onboard business organizations"
            );
        }

        String normalizedBusinessEmail =
                normalizeEmail(request.businessEmail());

        checkBusinessEmail(normalizedBusinessEmail);

        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setBusinessEmail(normalizedBusinessEmail);
        organization.setPhone(normalizeOptional(request.phone()));
        organization.setAddress(normalizeOptional(request.address()));

        Organization savedOrganization =
                organizationRepository.save(organization);

        for (OrganizationCapability capability : request.capabilities()) {

            organizationCapabilityRepository.save(
                    new OrganizationCapabilityEntity(
                            savedOrganization,
                            capability
                    )
            );

            organizationCapabilityAuditRepository.save(
                    new OrganizationCapabilityAudit(
                            savedOrganization,
                            capability,
                            OrganizationCapabilityAction.ENABLED,
                            user,
                            "Organization onboarding"
                    )
            );
        }

        OrganizationMembership ownerMembership = new OrganizationMembership(
                user,
                savedOrganization,
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE,
                LocalDateTime.now()
        );

        organizationMembershipRepository.save(ownerMembership);

        return new OrganizationOnboardResponse(
                "Organization created successfully",
                savedOrganization.getId(),
                savedOrganization.getName()
        );
    }

    /**
     * Only administrators can list every organization.
     */
    public List<Organization> getAllOrganizations(
            String authenticatedEmail
    ) {

        User user = getAuthenticatedUser(authenticatedEmail);
        requireAdmin(user);

        return organizationRepository.findAll();
    }

    /**
     * Administrators can access any organization.
     * Other users require an ACTIVE membership in the requested organization.
     */
    public Organization getOrganizationById(
            Long id,
            String authenticatedEmail
    ) {

        User user = getAuthenticatedUser(authenticatedEmail);

        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organization not found"
                ));

        if (user.getRole() == UserRole.ADMIN) {
            return organization;
        }

        OrganizationMembership membership =
                organizationMembershipRepository
                        .findByUserIdAndOrganizationId(user.getId(), id)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "You are not authorized to access this organization"
                        ));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to access this organization"
            );
        }

        return organization;
    }

    private User getAuthenticatedUser(String authenticatedEmail) {

        String normalizedEmail = normalizeEmail(authenticatedEmail);

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

    private void requireAdmin(User user) {

        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only administrators can access all organizations"
            );
        }
    }

    private void checkBusinessEmail(String email) {

        if (organizationRepository.existsByBusinessEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An organization with this business email already exists"
            );
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}