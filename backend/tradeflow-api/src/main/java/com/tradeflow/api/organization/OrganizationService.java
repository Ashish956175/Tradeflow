package com.tradeflow.api.organization;

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
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            UserRepository userRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
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

        if (user.getOrganization() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Your account is already associated with an organization"
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

        user.setOrganization(savedOrganization);
        userRepository.save(user);

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
     * Buyers and suppliers can access only their own organization.
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

        if (user.getOrganization() == null
                || !user.getOrganization().getId().equals(organization.getId())) {

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