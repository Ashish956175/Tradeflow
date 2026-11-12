package com.tradeflow.api.organization;


import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRepository;
import com.tradeflow.api.user.UserRole;

import com.tradeflow.api.membership.MembershipRole;
import com.tradeflow.api.membership.MembershipStatus;
import com.tradeflow.api.membership.OrganizationMembership;
import com.tradeflow.api.membership.OrganizationMembershipRepository;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

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
        buyer = createUser("buyer@example.com", UserRole.BUYER, true);
        supplier = createUser("supplier@example.com", UserRole.SUPPLIER, true);
        admin = createUser("admin@example.com", UserRole.ADMIN, true);
    }

    // =====================================================
    // TEST DATA HELPERS
    // =====================================================

    private User createUser(
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

        try {
            Field idField = Organization.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(organization, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Unable to assign organization ID in test",
                    exception
            );
        }

        return organization;
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
                    setOrganizationId(organization, 10L);
                    return organization;
                });

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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

        assertNotNull(buyer.getOrganization());
        assertEquals(
                "retail@example.com",
                buyer.getOrganization().getBusinessEmail()
        );

        // Verify organization capability persistence
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

        // Verify capability audit persistence
        ArgumentCaptor<OrganizationCapabilityAudit> auditCaptor =
                ArgumentCaptor.forClass(OrganizationCapabilityAudit.class);

        verify(organizationCapabilityAuditRepository)
                .save(auditCaptor.capture());

        OrganizationCapabilityAudit audit = auditCaptor.getValue();

        assertEquals(OrganizationCapability.BUYER, audit.getCapability());
        assertEquals(OrganizationCapabilityAction.ENABLED, audit.getAction());
        assertEquals(buyer, audit.getChangedBy());
        assertEquals("Organization onboarding", audit.getReason());

        // Verify active OWNER membership
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

    // Verify legacy user association is retained
        verify(userRepository).save(buyer);
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
                    setOrganizationId(organization, 20L);
                    return organization;
                });

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationOnboardResponse result =
                organizationService.onboardOrganization(
                        validOnboardRequest(OrganizationCapability.SUPPLIER),
                        supplier.getEmail()
                );

        assertEquals(20L, result.organizationId());
        assertEquals("TradeFlow Retailers", result.organizationName());
        assertNotNull(supplier.getOrganization());

        // Verify organization capability persistence
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

        // Verify capability audit persistence
        ArgumentCaptor<OrganizationCapabilityAudit> auditCaptor =
                ArgumentCaptor.forClass(OrganizationCapabilityAudit.class);

        verify(organizationCapabilityAuditRepository)
                .save(auditCaptor.capture());

        OrganizationCapabilityAudit audit = auditCaptor.getValue();

        assertEquals(OrganizationCapability.SUPPLIER, audit.getCapability());
        assertEquals(OrganizationCapabilityAction.ENABLED, audit.getAction());
        assertEquals(supplier, audit.getChangedBy());
        assertEquals("Organization onboarding", audit.getReason());

        // Verify active OWNER membership
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

        // Verify legacy association
        verify(userRepository).save(supplier);
    }

    @Test
    void onboardOrganization_shouldOnboardOrganizationWithBuyerAndSupplierCapabilities() {
        authenticate(buyer);

        when(organizationRepository.existsByBusinessEmail(
                "retail@example.com"
        )).thenReturn(false);

        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> {
                    Organization organization = invocation.getArgument(0);
                    setOrganizationId(organization, 30L);
                    return organization;
                });

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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
        assertNotNull(buyer.getOrganization());

        // Verify both organization capabilities
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

        // Verify both capability audit records
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

        // Verify active OWNER membership
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

        // Verify legacy association
        verify(userRepository).save(buyer);
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
    void onboardOrganization_shouldRejectExistingOrganizationAssignment() {
        Organization existing = createOrganization(
                1L,
                "Existing Organization",
                "existing@example.com"
        );

        buyer.setOrganization(existing);
        authenticate(buyer);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> organizationService.onboardOrganization(
                        validOnboardRequest(),
                        buyer.getEmail()
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
    }

    @Test
    void getOrganizationById_shouldAllowBuyerToAccessOwnOrganization() {
        Organization ownOrganization = createOrganization(
                10L,
                "Buyer's Organization",
                "buyer-business@example.com"
        );

        buyer.setOrganization(ownOrganization);
        authenticate(buyer);

        when(organizationRepository.findById(10L))
                .thenReturn(Optional.of(ownOrganization));

        Organization result =
                organizationService.getOrganizationById(
                        10L,
                        buyer.getEmail()
                );

        assertSame(ownOrganization, result);
    }

    @Test
    void getOrganizationById_shouldRejectAccessToAnotherOrganization() {
        Organization ownOrganization = createOrganization(
                10L,
                "Buyer's Organization",
                "buyer-business@example.com"
        );

        Organization anotherOrganization = createOrganization(
                20L,
                "Another Organization",
                "another@example.com"
        );

        buyer.setOrganization(ownOrganization);
        authenticate(buyer);

        when(organizationRepository.findById(20L))
                .thenReturn(Optional.of(anotherOrganization));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.getOrganizationById(
                        20L,
                        buyer.getEmail()
                )
        );
    }

    @Test
    void getOrganizationById_shouldRejectBuyerWithoutOrganization() {
        authenticate(buyer);

        Organization organization = createOrganization(
                10L,
                "Some Organization",
                "some@example.com"
        );

        when(organizationRepository.findById(10L))
                .thenReturn(Optional.of(organization));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> organizationService.getOrganizationById(
                        10L,
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

    // =====================================================
    // REFLECTION HELPER FOR GENERATED ENTITY IDS
    // =====================================================

    private void setOrganizationId(
            Organization organization,
            Long id
    ) {
        try {
            Field idField = Organization.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(organization, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Unable to assign organization ID in test",
                    exception
            );
        }
    }
}