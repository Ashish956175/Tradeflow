package com.tradeflow.api.relationship;

import com.tradeflow.api.membership.OrganizationMembership;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.organization.OrganizationCapabilityRepository;
import com.tradeflow.api.organization.OrganizationRepository;
import com.tradeflow.api.security.MembershipAuthorizationService;
import com.tradeflow.api.supplier.Supplier;
import com.tradeflow.api.supplier.SupplierRepository;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationshipServiceTest {

    @Mock
    private BuyerSupplierRelationshipRepository relationshipRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private OrganizationCapabilityRepository organizationCapabilityRepository;

    @Mock
    private MembershipAuthorizationService authorizationService;

    @InjectMocks
    private RelationshipService relationshipService;

    private Organization buyerOrganization;
    private Organization supplierOrganization;

    private Supplier supplier;
    private BuyerSupplierRelationship relationship;

    private User buyerUser;
    private User supplierUser;
    private User adminUser;

    private OrganizationMembership buyerMembership;
    private OrganizationMembership supplierMembership;

    private static final String BUYER_EMAIL = "buyer@example.com";
    private static final String SUPPLIER_EMAIL = "supplier@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";

    @BeforeEach
    void setUp() {

        buyerOrganization = createOrganization(
                10L, "Buyer Organization"
        );

        supplierOrganization = createOrganization(
                20L, "Supplier Organization"
        );

        supplier = new Supplier();
        ReflectionTestUtils.setField(supplier, "id", 30L);
        supplier.setOrganization(supplierOrganization);

        buyerUser = new User(
                "Buyer User",
                BUYER_EMAIL,
                "TestPasswordHash",
                UserRole.BUYER
        );

        supplierUser = new User(
                "Supplier User",
                SUPPLIER_EMAIL,
                "TestPasswordHash",
                UserRole.SUPPLIER
        );

        adminUser = new User(
                "Admin User",
                ADMIN_EMAIL,
                "TestPasswordHash",
                UserRole.ADMIN
        );

        buyerMembership = mock(OrganizationMembership.class);
        supplierMembership = mock(OrganizationMembership.class);

        relationship = new BuyerSupplierRelationship();

        ReflectionTestUtils.setField(relationship, "id", 1L);

        relationship.setBuyerOrganization(buyerOrganization);
        relationship.setSupplier(supplier);
        relationship.setStatus(RelationshipStatus.PENDING);
    }

    private Organization createOrganization(Long id, String name) {

        Organization organization = new Organization();

        ReflectionTestUtils.setField(organization, "id", id);

        organization.setName(name);

        organization.setBusinessEmail(
                name.toLowerCase().replace(" ", "") + "@example.com"
        );

        return organization;
    }

    private void authenticate(String email, User user) {

        when(authorizationService.getAuthenticatedUser(email))
                .thenReturn(user);
    }

    private void authorizeBuyerMembership(String email) {

        when(authorizationService.requireOrganizationCapability(
                email,
                10L,
                OrganizationCapability.BUYER
        )).thenReturn(buyerMembership);
    }

    private void authorizeBuyerMembershipForCreate(String email) {

        authorizeBuyerMembership(email);

        when(buyerMembership.getOrganization())
                .thenReturn(buyerOrganization);
    }

    private void authorizeSupplierMembership(String email) {

        when(authorizationService.requireOrganizationCapability(
                email,
                20L,
                OrganizationCapability.SUPPLIER
        )).thenReturn(supplierMembership);
    }

    private void assertStatus(
            HttpStatus expectedStatus,
            Runnable operation
    ) {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                operation::run
        );

        assertEquals(
                expectedStatus.value(),
                exception.getStatusCode().value()
        );
    }

    // =====================================================
    // CREATE RELATIONSHIP
    // =====================================================

    @Test
    void createRelationship_shouldCreatePendingRelationship() {

        authenticate(BUYER_EMAIL, buyerUser);
        authorizeBuyerMembershipForCreate(BUYER_EMAIL);

        when(supplierRepository.findById(30L))
                .thenReturn(Optional.of(supplier));

        when(organizationCapabilityRepository
                .existsByOrganizationIdAndCapability(
                        20L,
                        OrganizationCapability.SUPPLIER
                )).thenReturn(true);

        when(relationshipRepository
                .existsByBuyerOrganization_IdAndSupplier_Id(10L, 30L))
                .thenReturn(false);

        when(relationshipRepository.save(any(BuyerSupplierRelationship.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BuyerSupplierRelationship result =
                relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        BUYER_EMAIL,
                        10L
                );

        assertEquals(RelationshipStatus.PENDING, result.getStatus());
        assertSame(buyerOrganization, result.getBuyerOrganization());
        assertSame(supplier, result.getSupplier());

        verify(relationshipRepository)
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void createRelationship_shouldAllowSupplierGlobalRoleWithBuyerMembership() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeBuyerMembershipForCreate(SUPPLIER_EMAIL);

        when(supplierRepository.findById(30L))
                .thenReturn(Optional.of(supplier));

        when(organizationCapabilityRepository
                .existsByOrganizationIdAndCapability(
                        20L,
                        OrganizationCapability.SUPPLIER
                )).thenReturn(true);

        when(relationshipRepository
                .existsByBuyerOrganization_IdAndSupplier_Id(10L, 30L))
                .thenReturn(false);

        when(relationshipRepository.save(any(BuyerSupplierRelationship.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BuyerSupplierRelationship result =
                relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        SUPPLIER_EMAIL,
                        10L
                );

        assertEquals(RelationshipStatus.PENDING, result.getStatus());
    }

    @Test
    void createRelationship_shouldRejectPlatformAdmin() {

        authenticate(ADMIN_EMAIL, adminUser);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        ADMIN_EMAIL,
                        10L
                )
        );

        verify(supplierRepository, never()).findById(any());

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void createRelationship_shouldRejectWithoutBuyerCapability() {

        authenticate(BUYER_EMAIL, buyerUser);

        when(authorizationService.requireOrganizationCapability(
                BUYER_EMAIL,
                10L,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN
        ));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        BUYER_EMAIL,
                        10L
                )
        );

        verify(supplierRepository, never()).findById(any());
    }

    @Test
    void createRelationship_shouldRejectUnknownSupplier() {

        authenticate(BUYER_EMAIL, buyerUser);
        authorizeBuyerMembershipForCreate(BUYER_EMAIL);

        when(supplierRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(99L),
                        BUYER_EMAIL,
                        10L
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void createRelationship_shouldRejectSupplierWithoutCapability() {

        authenticate(BUYER_EMAIL, buyerUser);
        authorizeBuyerMembershipForCreate(BUYER_EMAIL);

        when(supplierRepository.findById(30L))
                .thenReturn(Optional.of(supplier));

        when(organizationCapabilityRepository
                .existsByOrganizationIdAndCapability(
                        20L,
                        OrganizationCapability.SUPPLIER
                )).thenReturn(false);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        BUYER_EMAIL,
                        10L
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void createRelationship_shouldRejectSameOrganization() {

        authenticate(BUYER_EMAIL, buyerUser);
        authorizeBuyerMembershipForCreate(BUYER_EMAIL);

        Supplier sameOrganizationSupplier = new Supplier();

        ReflectionTestUtils.setField(
                sameOrganizationSupplier, "id", 31L
        );

        sameOrganizationSupplier.setOrganization(buyerOrganization);

        when(supplierRepository.findById(31L))
                .thenReturn(Optional.of(sameOrganizationSupplier));

        assertStatus(
                HttpStatus.BAD_REQUEST,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(31L),
                        BUYER_EMAIL,
                        10L
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void createRelationship_shouldRejectDuplicateRelationship() {

        authenticate(BUYER_EMAIL, buyerUser);
        authorizeBuyerMembershipForCreate(BUYER_EMAIL);

        when(supplierRepository.findById(30L))
                .thenReturn(Optional.of(supplier));

        when(organizationCapabilityRepository
                .existsByOrganizationIdAndCapability(
                        20L,
                        OrganizationCapability.SUPPLIER
                )).thenReturn(true);

        when(relationshipRepository
                .existsByBuyerOrganization_IdAndSupplier_Id(10L, 30L))
                .thenReturn(true);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        BUYER_EMAIL,
                        10L
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    // =====================================================
    // GET BUYER RELATIONSHIPS
    // =====================================================

    @Test
    void getBuyerRelationships_shouldAllowAuthorizedBuyer() {

        authenticate(BUYER_EMAIL, buyerUser);
        authorizeBuyerMembership(BUYER_EMAIL);

        when(organizationRepository.findById(10L))
                .thenReturn(Optional.of(buyerOrganization));

        when(relationshipRepository.findAllByBuyerOrganization_Id(10L))
                .thenReturn(List.of(relationship));

        List<BuyerSupplierRelationship> result =
                relationshipService.getBuyerRelationships(
                        10L, BUYER_EMAIL
                );

        assertEquals(1, result.size());
    }

    @Test
    void getBuyerRelationships_shouldRejectWithoutBuyerCapability() {

        authenticate(BUYER_EMAIL, buyerUser);

        when(organizationRepository.findById(10L))
                .thenReturn(Optional.of(buyerOrganization));

        when(authorizationService.requireOrganizationCapability(
                BUYER_EMAIL,
                10L,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN
        ));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.getBuyerRelationships(
                        10L, BUYER_EMAIL
                )
        );

        verify(relationshipRepository, never())
                .findAllByBuyerOrganization_Id(any());
    }

    @Test
    void getBuyerRelationships_shouldAllowPlatformAdmin() {

        authenticate(ADMIN_EMAIL, adminUser);

        when(organizationRepository.findById(10L))
                .thenReturn(Optional.of(buyerOrganization));

        when(relationshipRepository.findAllByBuyerOrganization_Id(10L))
                .thenReturn(List.of(relationship));

        List<BuyerSupplierRelationship> result =
                relationshipService.getBuyerRelationships(
                        10L, ADMIN_EMAIL
                );

        assertEquals(1, result.size());

        verify(authorizationService, never())
                .requireOrganizationCapability(
                        any(), any(), any()
                );
    }

    @Test
    void getBuyerRelationships_shouldReturnNotFoundForUnknownOrganization() {

        authenticate(ADMIN_EMAIL, adminUser);

        when(organizationRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> relationshipService.getBuyerRelationships(
                        99L, ADMIN_EMAIL
                )
        );
    }

    // =====================================================
    // GET SUPPLIER RELATIONSHIPS
    // =====================================================

    @Test
    void getSupplierRelationships_shouldAllowAuthorizedSupplier() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplierMembership(SUPPLIER_EMAIL);

        when(supplierRepository.findById(30L))
                .thenReturn(Optional.of(supplier));

        when(relationshipRepository.findAllBySupplier_Id(30L))
                .thenReturn(List.of(relationship));

        List<BuyerSupplierRelationship> result =
                relationshipService.getSupplierRelationships(
                        30L, SUPPLIER_EMAIL
                );

        assertEquals(1, result.size());
    }

    @Test
    void getSupplierRelationships_shouldRejectWithoutSupplierCapability() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(supplierRepository.findById(30L))
                .thenReturn(Optional.of(supplier));

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                20L,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN
        ));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.getSupplierRelationships(
                        30L, SUPPLIER_EMAIL
                )
        );

        verify(relationshipRepository, never())
                .findAllBySupplier_Id(any());
    }

    @Test
    void getSupplierRelationships_shouldAllowPlatformAdmin() {

        authenticate(ADMIN_EMAIL, adminUser);

        when(supplierRepository.findById(30L))
                .thenReturn(Optional.of(supplier));

        when(relationshipRepository.findAllBySupplier_Id(30L))
                .thenReturn(List.of(relationship));

        List<BuyerSupplierRelationship> result =
                relationshipService.getSupplierRelationships(
                        30L, ADMIN_EMAIL
                );

        assertEquals(1, result.size());

        verify(authorizationService, never())
                .requireOrganizationCapability(
                        any(), any(), any()
                );
    }

    // =====================================================
    // APPROVE RELATIONSHIP
    // =====================================================

    @Test
    void approveRelationship_shouldActivatePendingRelationship() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplierMembership(SUPPLIER_EMAIL);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        when(relationshipRepository.save(relationship))
                .thenReturn(relationship);

        BuyerSupplierRelationship result =
                relationshipService.approveRelationship(
                        1L, SUPPLIER_EMAIL
                );

        assertEquals(RelationshipStatus.ACTIVE, result.getStatus());

        verify(relationshipRepository).save(relationship);
    }

    @Test
    void approveRelationship_shouldRejectPlatformAdmin() {

        authenticate(ADMIN_EMAIL, adminUser);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.approveRelationship(
                        1L, ADMIN_EMAIL
                )
        );

        verify(relationshipRepository, never()).findById(any());

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void approveRelationship_shouldRejectWithoutSupplierCapability() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                20L,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN
        ));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.approveRelationship(
                        1L, SUPPLIER_EMAIL
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void approveRelationship_shouldReturnNotFoundForMissingRelationship() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(relationshipRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> relationshipService.approveRelationship(
                        99L, SUPPLIER_EMAIL
                )
        );
    }

    @Test
    void approveRelationship_shouldRejectAlreadyActiveRelationship() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplierMembership(SUPPLIER_EMAIL);

        relationship.setStatus(RelationshipStatus.ACTIVE);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        assertStatus(
                HttpStatus.CONFLICT,
                () -> relationshipService.approveRelationship(
                        1L, SUPPLIER_EMAIL
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    // =====================================================
    // REJECT RELATIONSHIP
    // =====================================================

    @Test
    void rejectRelationship_shouldRejectPendingRelationship() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplierMembership(SUPPLIER_EMAIL);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        when(relationshipRepository.save(relationship))
                .thenReturn(relationship);

        BuyerSupplierRelationship result =
                relationshipService.rejectRelationship(
                        1L, SUPPLIER_EMAIL
                );

        assertEquals(RelationshipStatus.REJECTED, result.getStatus());

        verify(relationshipRepository).save(relationship);
    }

    @Test
    void rejectRelationship_shouldRejectPlatformAdmin() {

        authenticate(ADMIN_EMAIL, adminUser);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.rejectRelationship(
                        1L, ADMIN_EMAIL
                )
        );

        verify(relationshipRepository, never()).findById(any());

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void rejectRelationship_shouldRejectAlreadyRejectedRelationship() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplierMembership(SUPPLIER_EMAIL);

        relationship.setStatus(RelationshipStatus.REJECTED);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        assertStatus(
                HttpStatus.CONFLICT,
                () -> relationshipService.rejectRelationship(
                        1L, SUPPLIER_EMAIL
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }
}
