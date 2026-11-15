package com.tradeflow.api.security;

import com.tradeflow.api.membership.MembershipCapabilityRepository;
import com.tradeflow.api.membership.MembershipRole;
import com.tradeflow.api.membership.MembershipStatus;
import com.tradeflow.api.membership.OrganizationMembership;
import com.tradeflow.api.membership.OrganizationMembershipRepository;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.organization.OrganizationCapabilityRepository;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class MembershipAuthorizationService {

    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationCapabilityRepository organizationCapabilityRepository;
    private final MembershipCapabilityRepository membershipCapabilityRepository;

    public MembershipAuthorizationService(
            UserRepository userRepository,
            OrganizationMembershipRepository membershipRepository,
            OrganizationCapabilityRepository organizationCapabilityRepository,
            MembershipCapabilityRepository membershipCapabilityRepository
    ) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.organizationCapabilityRepository = organizationCapabilityRepository;
        this.membershipCapabilityRepository = membershipCapabilityRepository;
    }

    public User getAuthenticatedUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user not found"
            );
        }

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

    public User requirePlatformAdmin(String email) {
        User user = getAuthenticatedUser(email);

        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Platform administrator permission is required"
            );
        }

        return user;
    }

    public OrganizationMembership requireActiveMembership(
            String email,
            Long organizationId
    ) {
        User user = getAuthenticatedUser(email);

        if (organizationId == null || organizationId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid organization ID is required"
            );
        }

        OrganizationMembership membership =
                membershipRepository.findByUserIdAndOrganizationId(
                        user.getId(),
                        organizationId
                ).orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You do not belong to this organization"
                ));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your organization membership is not active"
            );
        }

        return membership;
    }

    public OrganizationMembership requireOrganizationCapability(
            String email,
            Long organizationId,
            OrganizationCapability capability
    ) {
        if (capability == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Organization capability is required"
            );
        }

        OrganizationMembership membership =
                requireActiveMembership(email, organizationId);

        boolean organizationHasCapability =
                organizationCapabilityRepository
                        .existsByOrganizationIdAndCapability(
                                organizationId,
                                capability
                        );

        if (!organizationHasCapability) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This organization does not have the required capability"
            );
        }

        if (membership.getMembershipRole() == MembershipRole.OWNER) {
            return membership;
        }

        boolean memberHasCapability =
                membershipCapabilityRepository
                        .existsByMembershipIdAndCapability(
                                membership.getId(),
                                capability
                        );

        if (!memberHasCapability) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have the required organization capability"
            );
        }

        return membership;
    }

    public OrganizationMembership requireAnyOrganizationCapability(
            String email,
            Long organizationId,
            OrganizationCapability... capabilities
    ) {
        if (capabilities == null || capabilities.length == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one organization capability is required"
            );
        }

        for (OrganizationCapability capability : capabilities) {
            if (capability == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Organization capability cannot be null"
                );
            }
        }

        OrganizationMembership membership =
                requireActiveMembership(email, organizationId);

        boolean hasEnabledOrganizationCapability = false;

        for (OrganizationCapability capability : capabilities) {
            if (organizationCapabilityRepository
                    .existsByOrganizationIdAndCapability(
                            organizationId,
                            capability
                    )) {
                hasEnabledOrganizationCapability = true;
                break;
            }
        }

        if (!hasEnabledOrganizationCapability) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Organization does not have any of the required capabilities enabled"
            );
        }

        if (membership.getMembershipRole() == MembershipRole.OWNER) {
            return membership;
        }

        for (OrganizationCapability capability : capabilities) {
            boolean organizationHasCapability =
                    organizationCapabilityRepository
                            .existsByOrganizationIdAndCapability(
                                    organizationId,
                                    capability
                            );

            if (organizationHasCapability
                    && membershipCapabilityRepository
                    .existsByMembershipIdAndCapability(
                            membership.getId(),
                            capability
                    )) {
                return membership;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Your membership does not have any of the required organization capabilities"
        );
    }

    public OrganizationMembership requireMembershipRole(
            String email,
            Long organizationId,
            MembershipRole requiredRole
    ) {
        if (requiredRole == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Membership role is required"
            );
        }

        OrganizationMembership membership =
                requireActiveMembership(email, organizationId);

        if (membership.getMembershipRole() != requiredRole) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have the required organization membership role"
            );
        }

        return membership;
    }
}
