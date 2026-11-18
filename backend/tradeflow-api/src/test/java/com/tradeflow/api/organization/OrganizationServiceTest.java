package com.tradeflow.api.organization;

import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import com.tradeflow.api.membership.MembershipRole;
import com.tradeflow.api.membership.MembershipStatus;
import com.tradeflow.api.membership.OrganizationMembership;
import com.tradeflow.api.membership.OrganizationMembershipRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationCapabilityRepository organizationCapabilityRepository;

    @Mock
    private OrganizationCapabilityAuditRepository organizationCapabilityAuditRepository;

    @Mock
    private OrganizationMembershipRepository organizationMembershipRepository;

    @InjectMocks
    private OrganizationService organizationService;

    private User buyer;
    private User supplier;
    private User admin;

    @BeforeEach
    void setUp() {
        buyer = createUser(1L, "buyer@example.com", UserRole.BUYER, true);
        supplier = createUser(2L, "supplier@example.com", UserRole.SUPPLIER, true);
        admin = createUser(3L, "admin@example.com", UserRole.ADMIN, true);
    }

    // =====================================================
    // TEST DATA HELPERS
    // =====================================================

    private User createUser(
            Long id,
            String email,
            UserRole role,
            boolean enabled
    ) {
        User user = new User(
                "Test User",
                email,
                "TestPasswordHash",
                role
        );

        user.setEnabled(enabled);
        setEntityId(user, id);

        return user;
    }

    private Organization createOrganization(
            Long id,
            String name,
            String email
    ) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setBusinessEmail(email);

        setEntityId(organization, id);

        return organization;
    }

    private OrganizationMembership createMembership(
            User user,
            Organization organization,
            MembershipRole role,
            MembershipStatus status
    ) {
        return new OrganizationMembership(
                user,
                organization,
                role,
                status,
                status == MembershipStatus.ACTIVE
                        ? java.time.LocalDateTime.now()
                        : null
        );
    }

    private OrganizationRequest validOrganizationRequest() {
        return new OrganizationRequest(
                "  TradeFlow Retailers  ",
                "  RETAIL@EXAMPLE.COM ",
                " 9876543210 ",
                " Pune, Maharashtra "
        );
    }

    private OrganizationOnboardRequest validOnboardRequest() {
        return validOnboardRequest(OrganizationCapability.BUYER);
    }

    private OrganizationOnboardRequest validOnboardRequest(
            OrganizationCapability... capabilities
    ) {
        return new OrganizationOnboardRequest(
                "  TradeFlow Retailers  ",
                "  RETAIL@EXAMPLE.COM ",
                " 9876543210 ",
                " Pune, Maharashtra ",
                Set.of(capabilities)
        );
    }

    private OrganizationOnboardRequest onboardRequest(
            String name,
            String email,
            OrganizationCapability... capabilities
    ) {
        return new OrganizationOnboardRequest(
                name,
                email,
                "9876543210",
                "Pune, Maharashtra",
                Set.of(capabilities)
        );
    }

    private void authenticate(User user) {
        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
    }

    private void assertStatus(
            HttpStatus expectedStatus,
            Runnable action
    ) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                action::run
        );

        assertEquals(
                expectedStatus.value(),
                exception.getStatusCode().value()
        );
    }

    private void setEntityId(Object entity, Long id) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Unable to assign entity ID in test",
                    exception
            );
        }
    }

    // =====================================================
    // CREATE ORGANIZATION
    // =====================================================

    @Test
    void createOrganization_shouldNormalizeAndSaveOrganization() {
        when(organizationRepository.existsByBusinessEmail(
                "retail@example.com"
        )).thenReturn(false);

        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Organization result =
                organizationService.createOrganization(
                        validOrganizationRequest()
                );

        assertEquals("TradeFlow Retailers", result.getName());
        assertEquals("retail@example.com", result.getBusinessEmail());
        assertEquals("9876543210", result.getPhone());
        assertEquals("Pune, Maharashtra", result.getAddress());

        verify(organizationRepository)
                .existsByBusinessEmail("retail@example.com");

        verify(organizationRepository).save(any(Organization.class));
    }

    @Test
    void createOrganization_shouldRejectDuplicateBusinessEmail() {
        when(organizationRepository.existsByBusinessEmail(
                "retail@example.com"
        )).thenReturn(true);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> organizationService.createOrganization(
                        validOrganizationRequest()
                )
        );

        verify(organizationRepository, never())
                .save(any(Organization.class));
    }

    @Test
    void createOrganization_shouldConvertBlankOptionalFieldsToNull() {
        OrganizationRequest request = new OrganizationRequest(
                "Test Organization",
                "test@example.com",
                "   ",
                null
        );

        when(organizationRepository.existsByBusinessEmail(
                "test@example.com"
        )).thenReturn(false);

        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Organization result =
                organizationService.createOrganization(request);

        assertNull(result.getPhone());
        assertNull(result.getAddress());
    }

    // =====================================================
    // ONBOARD ORGANIZATION
    // =====================================================

    @Test
    void onboardOrganization_shouldOnboardBuyerSuccessfully() {
        authenticate(buyer);

        when(organizationRepository.existsByBusinessEmail(
                "retail@example.com"
        )).thenReturn(false);

        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> {
                    Organization organization = invocation.getArgument(0);
                    setEntityId(organization, 10L);
                    return organization;
                });

        OrganizationOnboardResponse result =
                organizationService.onboardOrganization(
                        validOnboardRequest(),
                        " BUYER@EXAMPLE.COM "
                );

        assertEquals(
                "Organization created successfully",
                result.message()
        );

        assertEquals(10L, result.organizationId());
        assertEquals("TradeFlow Retailers", result.organizationName());

        // Verify organization capability persistence.
        ArgumentCaptor<OrganizationCapabilityEntity> capabilityCaptor =
                ArgumentCaptor.forClass(OrganizationCapabilityEntity.class);

        verify(organizationCapabilityRepository)
                .save(capabilityCaptor.capture());

        assertEquals(
                OrganizationCapability.BUYER,
                capabilityCaptor.getValue().getCapability()
        );

        assertEquals(
                10L,
                capabilityCaptor.getValue().getOrganization().getId()
        );

        // Verify capability audit persistence.
        ArgumentCaptor<OrganizationCapabilityAudit> auditCaptor =
                ArgumentCaptor.forClass(OrganizationCapabilityAudit.class);

        verify(organizationCapabilityAuditRepository)
                .save(auditCaptor.capture());

        OrganizationCapabilityAudit audit = auditCaptor.getValue();

        assertEquals(OrganizationCapability.BUYER, audit.getCapability());
        assertEquals(OrganizationCapabilityAction.ENABLED, audit.getAction());
        assertEquals(buyer, audit.getChangedBy());
        assertEquals("Organization onboarding", audit.getReason());

        // Verify active OWNER membership.
        ArgumentCaptor<OrganizationMembership> membershipCaptor =
                ArgumentCaptor.forClass(OrganizationMembership.class);

        verify(organizationMembershipRepository)
                .save(membershipCaptor.capture());

        OrganizationMembership membership = membershipCaptor.getValue();

        assertEquals(buyer, membership.getUser());
        assertEquals(10L, membership.getOrganization().getId());
        assertEquals(MembershipRole.OWNER, membership.getMembershipRole());
        assertEquals(MembershipStatus.ACTIVE, membership.getStatus());
        assertNotNull(membership.getJoinedAt());

        // Onboarding must not modify or save the legacy association.
        assertNull(buyer.getOrganization());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void onboardOrganization_shouldOnboardSupplierSuccessfully() {
        authenticate(supplier);

        when(organizationRepository.existsByBusinessEmail(
                "retail@example.com"
        )).thenReturn(false);

        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> {
                    Organization organization = invocation.getArgument(0);
                    setEntityId(organization, 20L);
                    return organization;
                });

        OrganizationOnboardResponse result =
                organizationService.onboardOrganization(
                        validOnboardRequest(OrganizationCapability.SUPPLIER),
                        supplier.getEmail()
                );

        assertEquals(20L, result.organizationId());
        assertEquals("TradeFlow Retailers", result.organizationName());

        ArgumentCaptor<OrganizationCapabilityEntity> capabilityCaptor =
                ArgumentCaptor.forClass(OrganizationCapabilityEntity.class);

        verify(organizationCapabilityRepository)
                .save(capabilityCaptor.capture());

        assertEquals(
                OrganizationCapability.SUPPLIER,
                capabilityCaptor.getValue().getCapability()
        );


        assertEquals(
                20L,
                capabilityCaptor.getValue().getOrganization().getId()
        );

        ArgumentCaptor<OrganizationCapabilityAudit> auditCaptor =
                ArgumentCaptor.forClass(OrganizationCapabilityAudit.class);

        verify(organizationCapabilityAuditRepository)
                .save(auditCaptor.capture());

        OrganizationCapabilityAudit audit = auditCaptor.getValue();

        assertEquals(OrganizationCapability.SUPPLIER, audit.getCapability());
        assertEquals(OrganizationCapabilityAction.ENABLED, audit.getAction());
        assertEquals(supplier, audit.getChangedBy());
        assertEquals("Organization onboarding", audit.getReason());

        ArgumentCaptor<OrganizationMembership> membershipCaptor =
                ArgumentCaptor.forClass(OrganizationMembership.class);

        verify(organizationMembershipRepository)
                .save(membershipCaptor.capture());

        OrganizationMembership membership = membershipCaptor.getValue();

        assertEquals(supplier, membership.getUser());
        assertEquals(20L, membership.getOrganization().getId());
        assertEquals(MembershipRole.OWNER, membership.getMembershipRole());
        assertEquals(MembershipStatus.ACTIVE, membership.getStatus());
        assertNotNull(membership.getJoinedAt());

        assertNull(supplier.getOrganization());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void onboardOrganization_shouldSupportBuyerAndSupplierCapabilities() {
        authenticate(buyer);

        when(organizationRepository.existsByBusinessEmail(
                "retail@example.com"
        )).thenReturn(false);

        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> {
                    Organization organization = invocation.getArgument(0);
                    setEntityId(organization, 30L);
                    return organization;
                });

        OrganizationOnboardResponse result =
                organizationService.onboardOrganization(
                        validOnboardRequest(
                                OrganizationCapability.BUYER,
                                OrganizationCapability.SUPPLIER
                        ),
                        buyer.getEmail()
                );

        assertEquals(30L, result.organizationId());
        assertEquals("TradeFlow Retailers", result.organizationName());

        ArgumentCaptor<OrganizationCapabilityEntity> capabilityCaptor =
                ArgumentCaptor.forClass(OrganizationCapabilityEntity.class);

        verify(organizationCapabilityRepository, times(2))
                .save(capabilityCaptor.capture());

        assertEquals(2, capabilityCaptor.getAllValues().size());

        assertTrue(capabilityCaptor.getAllValues().stream()
                .anyMatch(capability ->
                        capability.getCapability() == OrganizationCapability.BUYER
                ));

        assertTrue(capabilityCaptor.getAllValues().stream()
                .anyMatch(capability ->
                        capability.getCapability() == OrganizationCapability.SUPPLIER
                ));

        ArgumentCaptor<OrganizationCapabilityAudit> auditCaptor =
                ArgumentCaptor.forClass(OrganizationCapabilityAudit.class);

        verify(organizationCapabilityAuditRepository, times(2))
                .save(auditCaptor.capture());
        assertEquals(2, auditCaptor.getAllValues().size());

        assertTrue(auditCaptor.getAllValues().stream()
                .allMatch(audit ->
                        audit.getAction() == OrganizationCapabilityAction.ENABLED
                                && audit.getChangedBy().equals(buyer)
                                && "Organization onboarding".equals(audit.getReason())
                ));

        ArgumentCaptor<OrganizationMembership> membershipCaptor =
                ArgumentCaptor.forClass(OrganizationMembership.class);

        verify(organizationMembershipRepository)
                .save(membershipCaptor.capture());

        OrganizationMembership membership = membershipCaptor.getValue();

        assertEquals(buyer, membership.getUser());
        assertEquals(30L, membership.getOrganization().getId());
        assertEquals(MembershipRole.OWNER, membership.getMembershipRole());
        assertEquals(MembershipStatus.ACTIVE, membership.getStatus());
        assertNotNull(membership.getJoinedAt());

        assertNull(buyer.getOrganization());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void onboardOrganization_shouldAllowSameUserToOnboardMultipleOrganizations() {
        authenticate(buyer);

        Organization legacyOrganization = createOrganization(
                5L,
                "Legacy Organization",
                "legacy@example.com"
        );

        buyer.setOrganization(legacyOrganization);

        when(organizationRepository.existsByBusinessEmail(any(String.class)))
                .thenReturn(false);

        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(new org.mockito.stubbing.Answer<Organization>() {
                    private long nextId = 40L;

                    @Override
                    public Organization answer(
                            org.mockito.invocation.InvocationOnMock invocation
                    ) {
                        Organization organization = invocation.getArgument(0);
                        setEntityId(organization, nextId++);
                        return organization;
                    }
                });

        OrganizationOnboardResponse first =
                organizationService.onboardOrganization(
                        onboardRequest(
                                "First Organization",
                                "first@example.com",
                                OrganizationCapability.BUYER
                        ),
                        buyer.getEmail()
                );

        OrganizationOnboardResponse second =
                organizationService.onboardOrganization(
                        onboardRequest(
                                "Second Organization",
                                "second@example.com",
                                OrganizationCapability.SUPPLIER
                        ),
                        buyer.getEmail()
                );

        assertEquals(40L, first.organizationId());
        assertEquals(41L, second.organizationId());

        ArgumentCaptor<OrganizationMembership> membershipCaptor =
                ArgumentCaptor.forClass(OrganizationMembership.class);

        verify(organizationMembershipRepository, times(2))
                .save(membershipCaptor.capture());

        List<OrganizationMembership> memberships =
                membershipCaptor.getAllValues();

        assertEquals(2, memberships.size());

        assertTrue(memberships.stream()
                .allMatch(membership ->
                        membership.getUser().equals(buyer)
                                && membership.getMembershipRole() == MembershipRole.OWNER
                                && membership.getStatus() == MembershipStatus.ACTIVE
                ));

        assertEquals(
                Set.of(40L, 41L),
                memberships.stream()
                        .map(membership -> membership.getOrganization().getId())
                        .collect(java.util.stream.Collectors.toSet())
        );

        // Existing legacy association must not be overwritten.
        assertSame(legacyOrganization, buyer.getOrganization());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void onboardOrganization_shouldRejectUnknownUser() {
        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.UNAUTHORIZED,
                () -> organizationService.onboardOrganization(
                        validOnboardRequest(),
                        "missing@example.com"
                )
        );

        verify(organizationRepository, never())
                .save(any(Organization.class));
    }

    @Test
    void onboardOrganization_shouldRejectDisabledUser() {
        buyer.setEnabled(false);
        authenticate(buyer);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.onboardOrganization(
                        validOnboardRequest(),
                        buyer.getEmail()
                )
        );

        verify(organizationRepository, never())
                .save(any(Organization.class));
    }

    @Test
    void onboardOrganization_shouldRejectAdminAccount() {
        authenticate(admin);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.onboardOrganization(
                        validOnboardRequest(),
                        admin.getEmail()
                )
        );

        verify(organizationRepository, never())
                .save(any(Organization.class));
    }

    @Test
    void onboardOrganization_shouldRejectDuplicateBusinessEmail() {
        authenticate(buyer);

        when(organizationRepository.existsByBusinessEmail(
                "retail@example.com"
        )).thenReturn(true);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> organizationService.onboardOrganization(
                        validOnboardRequest(),
                        buyer.getEmail()
                )
        );

        verify(organizationRepository, never())
                .save(any(Organization.class));

        verify(organizationMembershipRepository, never())
                .save(any(OrganizationMembership.class));

        verify(userRepository, never()).save(any(User.class));
    }

    // =====================================================
    // GET ALL ORGANIZATIONS
    // =====================================================

    @Test
    void getAllOrganizations_shouldReturnOrganizationsForAdmin() {
        authenticate(admin);

        List<Organization> organizations = List.of(
                createOrganization(
                        1L,
                        "Retailer One",
                        "retailer1@example.com"
                ),
                createOrganization(
                        2L,
                        "Supplier One",
                        "supplier1@example.com"
                )
        );

        when(organizationRepository.findAll())
                .thenReturn(organizations);

        List<Organization> result =
                organizationService.getAllOrganizations(
                        admin.getEmail()
                );

        assertEquals(2, result.size());
        assertEquals("Retailer One", result.get(0).getName());

        verify(organizationRepository).findAll();
    }

    @Test
    void getAllOrganizations_shouldRejectBuyer() {
        authenticate(buyer);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.getAllOrganizations(
                        buyer.getEmail()
                )
        );

        verify(organizationRepository, never()).findAll();
    }

    @Test
    void getAllOrganizations_shouldRejectSupplier() {
        authenticate(supplier);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.getAllOrganizations(
                        supplier.getEmail()
                )
        );

        verify(organizationRepository, never()).findAll();
    }

    @Test
    void getAllOrganizations_shouldRejectDisabledAdmin() {
        admin.setEnabled(false);
        authenticate(admin);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.getAllOrganizations(
                        admin.getEmail()
                )
        );

        verify(organizationRepository, never()).findAll();
    }

    // =====================================================
    // GET ORGANIZATION BY ID
    // =====================================================

    @Test
    void getOrganizationById_shouldAllowAdminToAccessAnyOrganization() {
        authenticate(admin);

        Organization organization = createOrganization(
                100L,
                "Any Organization",
                "any@example.com"
        );

        when(organizationRepository.findById(100L))
                .thenReturn(Optional.of(organization));

        Organization result =
                organizationService.getOrganizationById(
                        100L,
                        admin.getEmail()
                );

        assertSame(organization, result);

        verify(organizationMembershipRepository, never())
                .findByUserIdAndOrganizationId(any(), any());
    }

    @Test
    void getOrganizationById_shouldAllowActiveBuyerMembership() {
        authenticate(buyer);

        Organization organization = createOrganization(
                10L,
                "Buyer's Organization",
                "buyer-business@example.com"
        );

        when(organizationRepository.findById(10L))
                .thenReturn(Optional.of(organization));

        when(organizationMembershipRepository.findByUserIdAndOrganizationId(
                buyer.getId(),
                10L
        )).thenReturn(Optional.of(
                createMembership(
                        buyer,
                        organization,
                        MembershipRole.OWNER,
                        MembershipStatus.ACTIVE
                )
        ));

        Organization result =
                organizationService.getOrganizationById(
                        10L,
                        buyer.getEmail()
                );

        assertSame(organization, result);

        verify(organizationMembershipRepository)
                .findByUserIdAndOrganizationId(buyer.getId(), 10L);
    }

    @Test
    void getOrganizationById_shouldAllowActiveMemberWithoutCapability() {
        authenticate(buyer);

        Organization organization = createOrganization(
                11L,
                "Member Organization",
                "member-business@example.com"
        );

        when(organizationRepository.findById(11L))
                .thenReturn(Optional.of(organization));

        when(organizationMembershipRepository.findByUserIdAndOrganizationId(
                buyer.getId(),
                11L
        )).thenReturn(Optional.of(
                createMembership(
                        buyer,
                        organization,
                        MembershipRole.MEMBER,
                        MembershipStatus.ACTIVE
                )
        ));

        Organization result =
                organizationService.getOrganizationById(
                        11L,
                        buyer.getEmail()
                );

        assertSame(organization, result);
    }

    @Test
    void getOrganizationById_shouldRejectInactiveMembership() {
        authenticate(buyer);

        Organization organization = createOrganization(
                12L,
                "Inactive Membership Organization",
                "inactive@example.com"
        );

        when(organizationRepository.findById(12L))
                .thenReturn(Optional.of(organization));

        when(organizationMembershipRepository.findByUserIdAndOrganizationId(
                buyer.getId(),
                12L
        )).thenReturn(Optional.of(
                createMembership(
                        buyer,
                        organization,
                        MembershipRole.MEMBER,
                        MembershipStatus.SUSPENDED
                )
        ));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.getOrganizationById(
                        12L,
                        buyer.getEmail()
                )
        );
    }

    @Test
    void getOrganizationById_shouldRejectPendingMembership() {
        authenticate(buyer);

        Organization organization = createOrganization(
                13L,
                "Pending Membership Organization",
                "pending@example.com"
        );

        when(organizationRepository.findById(13L))
                .thenReturn(Optional.of(organization));

        when(organizationMembershipRepository.findByUserIdAndOrganizationId(
                buyer.getId(),
                13L
        )).thenReturn(Optional.of(
                createMembership(
                        buyer,
                        organization,
                        MembershipRole.MEMBER,
                        MembershipStatus.INVITED
                )
        ));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.getOrganizationById(
                        13L,
                        buyer.getEmail()
                )
        );
    }

    @Test
    void getOrganizationById_shouldRejectAccessToAnotherOrganization() {
        authenticate(buyer);

        Organization anotherOrganization = createOrganization(
                20L,
                "Another Organization",
                "another@example.com"
        );

        when(organizationRepository.findById(20L))
                .thenReturn(Optional.of(anotherOrganization));

        when(organizationMembershipRepository.findByUserIdAndOrganizationId(
                buyer.getId(),
                20L
        )).thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.getOrganizationById(
                        20L,
                        buyer.getEmail()
                )
        );
    }

    @Test
    void getOrganizationById_shouldRejectUserWithoutMembership() {
        authenticate(buyer);

        Organization organization = createOrganization(
                21L,
                "Some Organization",
                "some@example.com"
        );

        when(organizationRepository.findById(21L))
                .thenReturn(Optional.of(organization));

        when(organizationMembershipRepository.findByUserIdAndOrganizationId(
                buyer.getId(),
                21L
        )).thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.getOrganizationById(
                        21L,
                        buyer.getEmail()
                )
        );
    }

    @Test
    void getOrganizationById_shouldReturnNotFoundForMissingOrganization() {
        authenticate(buyer);

        when(organizationRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> organizationService.getOrganizationById(
                        999L,
                        buyer.getEmail()
                )
        );

        verify(organizationMembershipRepository, never())
                .findByUserIdAndOrganizationId(any(), any());
    }

    @Test
    void getOrganizationById_shouldRejectDisabledUser() {
        buyer.setEnabled(false);
        authenticate(buyer);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.getOrganizationById(
                        10L,
                        buyer.getEmail()
                )
        );

        verify(organizationRepository, never()).findById(any());
    }

    @Test
    void getOrganizationById_shouldRejectUnknownUser() {
        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.UNAUTHORIZED,
                () -> organizationService.getOrganizationById(
                        10L,
                        "missing@example.com"
                )
        );

        verify(organizationRepository, never()).findById(any());
    }
}
