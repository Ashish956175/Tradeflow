package com.tradeflow.api.relationship;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationRepository;
import com.tradeflow.api.relationship.*;
import com.tradeflow.api.supplier.Supplier;
import com.tradeflow.api.supplier.SupplierRepository;
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
    private UserRepository userRepository;

    @InjectMocks
    private RelationshipService relationshipService;

    private Organization buyerOrganization;
    private Organization supplierOrganization;
    private Supplier supplier;
    private BuyerSupplierRelationship relationship;

    private User buyerUser;
    private User supplierUser;
    private User adminUser;

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

        buyerUser = createUser(
                BUYER_EMAIL, UserRole.BUYER, buyerOrganization
        );

        supplierUser = createUser(
                SUPPLIER_EMAIL, UserRole.SUPPLIER, supplierOrganization
        );

        adminUser = createUser(
                ADMIN_EMAIL, UserRole.ADMIN, null
        );

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

    private User createUser(
            String email,
            UserRole role,
            Organization organization
    ) {
        User user = new User(
                "Test User",
                email,
                "TestPasswordHash",
                role
        );

        user.setOrganization(organization);
        return user;
    }

    private void authenticate(User user) {
        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
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
    void createRelationship_shouldCreatePendingRelationshipForBuyer() {
        authenticate(buyerUser);

        when(supplierRepository.findById(30L))
                .thenReturn(Optional.of(supplier));

        when(relationshipRepository
                .existsByBuyerOrganization_IdAndSupplier_Id(10L, 30L))
                .thenReturn(false);

        when(relationshipRepository.save(any(BuyerSupplierRelationship.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BuyerSupplierRelationship result =
                relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        BUYER_EMAIL
                );

        assertEquals(RelationshipStatus.PENDING, result.getStatus());
        assertSame(buyerOrganization, result.getBuyerOrganization());
        assertSame(supplier, result.getSupplier());

        verify(relationshipRepository).save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void createRelationship_shouldRejectNonBuyer() {
        authenticate(supplierUser);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        SUPPLIER_EMAIL
                )
        );

        verify(supplierRepository, never()).findById(any());
        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void createRelationship_shouldRejectBuyerWithoutOrganization() {
        User buyerWithoutOrganization = createUser(
                "unassigned@example.com",
                UserRole.BUYER,
                null
        );

        authenticate(buyerWithoutOrganization);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        "unassigned@example.com"
                )
        );

        verify(supplierRepository, never()).findById(any());
    }

    @Test
    void createRelationship_shouldRejectUnknownSupplier() {
        authenticate(buyerUser);

        when(supplierRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(99L),
                        BUYER_EMAIL
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void createRelationship_shouldRejectDuplicateRelationship() {
        authenticate(buyerUser);

        when(supplierRepository.findById(30L))
                .thenReturn(Optional.of(supplier));

        when(relationshipRepository
                .existsByBuyerOrganization_IdAndSupplier_Id(10L, 30L))
                .thenReturn(true);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        BUYER_EMAIL
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void createRelationship_shouldRejectSameBuyerAndSupplierOrganization() {
        authenticate(buyerUser);

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
                        BUYER_EMAIL
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void createRelationship_shouldRejectUnknownAuthenticatedUser() {
        when(userRepository.findByEmail(BUYER_EMAIL))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.UNAUTHORIZED,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        BUYER_EMAIL
                )
        );
    }

    @Test
    void createRelationship_shouldRejectDisabledBuyer() {
        buyerUser.setEnabled(false);
        authenticate(buyerUser);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.createRelationship(
                        new RelationshipRequest(30L),
                        BUYER_EMAIL
                )
        );

        verify(supplierRepository, never()).findById(any());
    }

    // =====================================================
    // APPROVE RELATIONSHIP
    // =====================================================

    @Test
    void approveRelationship_shouldActivatePendingRelationship() {
        authenticate(supplierUser);

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
    void approveRelationship_shouldAllowAdmin() {
        authenticate(adminUser);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        when(relationshipRepository.save(relationship))
                .thenReturn(relationship);

        BuyerSupplierRelationship result =
                relationshipService.approveRelationship(
                        1L, ADMIN_EMAIL
                );

        assertEquals(RelationshipStatus.ACTIVE, result.getStatus());
    }

    @Test
    void approveRelationship_shouldRejectBuyer() {
        authenticate(buyerUser);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.approveRelationship(
                        1L, BUYER_EMAIL
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void approveRelationship_shouldRejectAnotherSupplier() {
        Organization anotherOrganization = createOrganization(
                40L, "Another Supplier"
        );

        User anotherSupplierUser = createUser(
                "other-supplier@example.com",
                UserRole.SUPPLIER,
                anotherOrganization
        );

        authenticate(anotherSupplierUser);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.approveRelationship(
                        1L, "other-supplier@example.com"
                )
        );

        verify(relationshipRepository, never())
                .save(any(BuyerSupplierRelationship.class));
    }

    @Test
    void approveRelationship_shouldRejectAlreadyActiveRelationship() {
        authenticate(supplierUser);
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

    @Test
    void approveRelationship_shouldReturnNotFoundForMissingRelationship() {
        authenticate(supplierUser);

        when(relationshipRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> relationshipService.approveRelationship(
                        99L, SUPPLIER_EMAIL
                )
        );
    }

    // =====================================================
    // REJECT RELATIONSHIP
    // =====================================================

    @Test
    void rejectRelationship_shouldRejectPendingRelationship() {
        authenticate(supplierUser);

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
    void rejectRelationship_shouldRejectAlreadyRejectedRelationship() {
        authenticate(supplierUser);
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

    @Test
    void rejectRelationship_shouldRejectBuyer() {
        authenticate(buyerUser);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.rejectRelationship(
                        1L, BUYER_EMAIL
                )
        );
    }

    @Test
    void rejectRelationship_shouldAllowAdmin() {
        authenticate(adminUser);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        when(relationshipRepository.save(relationship))
                .thenReturn(relationship);

        BuyerSupplierRelationship result =
                relationshipService.rejectRelationship(
                        1L, ADMIN_EMAIL
                );

        assertEquals(RelationshipStatus.REJECTED, result.getStatus());
    }

    // =====================================================
    // LIST RELATIONSHIPS
    // =====================================================

    @Test
    void getBuyerRelationships_shouldAllowBuyerToViewOwnOrganization() {
        authenticate(buyerUser);

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
    void getBuyerRelationships_shouldRejectBuyerViewingAnotherOrganization() {
        authenticate(buyerUser);

        Organization anotherOrganization = createOrganization(
                99L, "Another Buyer"
        );

        when(organizationRepository.findById(99L))
                .thenReturn(Optional.of(anotherOrganization));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.getBuyerRelationships(
                        99L, BUYER_EMAIL
                )
        );

        verify(relationshipRepository, never())
                .findAllByBuyerOrganization_Id(any());
    }

    @Test
    void getSupplierRelationships_shouldAllowSupplierToViewOwnRelationships() {
        authenticate(supplierUser);

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
    void getSupplierRelationships_shouldRejectSupplierViewingAnotherSupplier() {
        Organization anotherOrganization = createOrganization(
                40L, "Another Supplier"
        );

        User anotherSupplierUser = createUser(
                "other-supplier@example.com",
                UserRole.SUPPLIER,
                anotherOrganization
        );

        authenticate(anotherSupplierUser);

        when(supplierRepository.findById(30L))
                .thenReturn(Optional.of(supplier));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> relationshipService.getSupplierRelationships(
                        30L, "other-supplier@example.com"
                )
        );

        verify(relationshipRepository, never())
                .findAllBySupplier_Id(any());
    }
}
