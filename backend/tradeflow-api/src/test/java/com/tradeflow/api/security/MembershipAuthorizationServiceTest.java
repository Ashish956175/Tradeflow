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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipAuthorizationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    @Mock
    private OrganizationCapabilityRepository organizationCapabilityRepository;

    @Mock
    private MembershipCapabilityRepository membershipCapabilityRepository;

    @InjectMocks
    private MembershipAuthorizationService authorizationService;

    private User user;
    private OrganizationMembership membership;

    private static final String EMAIL = "buyer@example.com";
    private static final Long USER_ID = 10L;
    private static final Long ORGANIZATION_ID = 20L;
    private static final Long MEMBERSHIP_ID = 30L;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        membership = mock(OrganizationMembership.class);

        lenient().when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(user));

        lenient().when(user.getId()).thenReturn(USER_ID);
        lenient().when(user.isEnabled()).thenReturn(true);

        lenient().when(membershipRepository.findByUserIdAndOrganizationId(
                USER_ID,
                ORGANIZATION_ID
        )).thenReturn(Optional.of(membership));

        lenient().when(membership.getId()).thenReturn(MEMBERSHIP_ID);
        lenient().when(membership.getStatus())
                .thenReturn(MembershipStatus.ACTIVE);
    }

    @Test
    void getAuthenticatedUser_shouldReturnEnabledUser() {
        User result = authorizationService.getAuthenticatedUser(
                " BUYER@EXAMPLE.COM "
        );

        assertSame(user, result);

        verify(userRepository).findByEmail(EMAIL);
    }

    @Test
    void getAuthenticatedUser_shouldRejectMissingUser() {
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.getAuthenticatedUser(EMAIL)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    @Test
    void getAuthenticatedUser_shouldRejectDisabledUser() {
        when(user.isEnabled()).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.getAuthenticatedUser(EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void requirePlatformAdmin_shouldAllowPlatformAdmin() {
        when(user.getRole()).thenReturn(UserRole.ADMIN);

        User result = authorizationService.requirePlatformAdmin(EMAIL);

        assertSame(user, result);
    }

    @Test
    void requirePlatformAdmin_shouldRejectNonAdmin() {
        when(user.getRole()).thenReturn(UserRole.BUYER);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.requirePlatformAdmin(EMAIL)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void requireActiveMembership_shouldReturnActiveMembership() {
        OrganizationMembership result =
                authorizationService.requireActiveMembership(
                        EMAIL,
                        ORGANIZATION_ID
                );

        assertSame(membership, result);
    }

    @Test
    void requireActiveMembership_shouldRejectSuspendedMembership() {
        when(membership.getStatus()).thenReturn(MembershipStatus.SUSPENDED);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.requireActiveMembership(
                        EMAIL,
                        ORGANIZATION_ID
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void requireActiveMembership_shouldRejectInvalidOrganizationId() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.requireActiveMembership(
                        EMAIL,
                        0L
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void requireOrganizationCapability_shouldAllowOwnerWithoutExplicitAssignment() {
        when(membership.getMembershipRole()).thenReturn(MembershipRole.OWNER);

        when(organizationCapabilityRepository
                .existsByOrganizationIdAndCapability(
                        ORGANIZATION_ID,
                        OrganizationCapability.BUYER
                )).thenReturn(true);

        OrganizationMembership result =
                authorizationService.requireOrganizationCapability(
                        EMAIL,
                        ORGANIZATION_ID,
                        OrganizationCapability.BUYER
                );

        assertSame(membership, result);

        verify(membershipCapabilityRepository, never())
                .existsByMembershipIdAndCapability(
                        anyLong(),
                        any(OrganizationCapability.class)
                );
    }

    @Test
    void requireOrganizationCapability_shouldAllowMemberWithExplicitCapability() {
        when(membership.getMembershipRole()).thenReturn(MembershipRole.MEMBER);

        when(organizationCapabilityRepository
                .existsByOrganizationIdAndCapability(
                        ORGANIZATION_ID,
                        OrganizationCapability.BUYER
                )).thenReturn(true);

        when(membershipCapabilityRepository
                .existsByMembershipIdAndCapability(
                        MEMBERSHIP_ID,
                        OrganizationCapability.BUYER
                )).thenReturn(true);

        OrganizationMembership result =
                authorizationService.requireOrganizationCapability(
                        EMAIL,
                        ORGANIZATION_ID,
                        OrganizationCapability.BUYER
                );

        assertSame(membership, result);
    }

    @Test
    void requireOrganizationCapability_shouldAllowAdminWithExplicitCapability() {
        when(membership.getMembershipRole()).thenReturn(MembershipRole.ADMIN);

        when(organizationCapabilityRepository
                .existsByOrganizationIdAndCapability(
                        ORGANIZATION_ID,
                        OrganizationCapability.SUPPLIER
                )).thenReturn(true);

        when(membershipCapabilityRepository
                .existsByMembershipIdAndCapability(
                        MEMBERSHIP_ID,
                        OrganizationCapability.SUPPLIER
                )).thenReturn(true);

        OrganizationMembership result =
                authorizationService.requireOrganizationCapability(
                        EMAIL,
                        ORGANIZATION_ID,
                        OrganizationCapability.SUPPLIER
                );

        assertSame(membership, result);
    }

    @Test
    void requireOrganizationCapability_shouldRejectMemberWithoutExplicitCapability() {
        when(membership.getMembershipRole()).thenReturn(MembershipRole.MEMBER);

        when(organizationCapabilityRepository
                .existsByOrganizationIdAndCapability(
                        ORGANIZATION_ID,
                        OrganizationCapability.BUYER
                )).thenReturn(true);

        when(membershipCapabilityRepository
                .existsByMembershipIdAndCapability(
                        MEMBERSHIP_ID,
                        OrganizationCapability.BUYER
                )).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.requireOrganizationCapability(
                        EMAIL,
                        ORGANIZATION_ID,
                        OrganizationCapability.BUYER
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void requireOrganizationCapability_shouldRejectDisabledOrganizationCapability() {
        when(organizationCapabilityRepository
                .existsByOrganizationIdAndCapability(
                        ORGANIZATION_ID,
                        OrganizationCapability.BUYER
                )).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.requireOrganizationCapability(
                        EMAIL,
                        ORGANIZATION_ID,
                        OrganizationCapability.BUYER
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(membershipCapabilityRepository, never())
                .existsByMembershipIdAndCapability(
                        anyLong(),
                        any(OrganizationCapability.class)
                );
    }

    @Test
    void requireMembershipRole_shouldRejectIncorrectRole() {
        when(membership.getMembershipRole()).thenReturn(MembershipRole.MEMBER);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authorizationService.requireMembershipRole(
                        EMAIL,
                        ORGANIZATION_ID,
                        MembershipRole.OWNER
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }
}
