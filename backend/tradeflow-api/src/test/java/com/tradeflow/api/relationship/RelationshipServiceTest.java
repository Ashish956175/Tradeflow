package com.tradeflow.api.relationship;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationRepository;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private BuyerSupplierRelationship relationship;

    private User supplierUser;

    private Organization supplierOrganization;

    private Supplier supplier;

    private static final String SUPPLIER_EMAIL = "supplier@example.com";

    @BeforeEach
    void setUp() {

        // Create supplier organization
        supplierOrganization = new Organization();

        ReflectionTestUtils.setField(
                supplierOrganization,
                "id",
                20L
        );

        supplierOrganization.setName("Supplier Organization");

        // Mock authenticated supplier user
        supplierUser = mock(User.class);

        // Create supplier profile
        supplier = new Supplier();

        ReflectionTestUtils.setField(
                supplier,
                "id",
                30L
        );

        supplier.setOrganization(supplierOrganization);

        // Create buyer organization
        Organization buyerOrganization = new Organization();

        ReflectionTestUtils.setField(
                buyerOrganization,
                "id",
                10L
        );

        buyerOrganization.setName("Buyer Organization");

        // Create buyer-supplier relationship
        relationship = new BuyerSupplierRelationship();

        relationship.setStatus(RelationshipStatus.PENDING);

        relationship.setBuyerOrganization(buyerOrganization);

        relationship.setSupplier(supplier);
    }

    // Configure an authenticated, enabled user
    private void mockAuthenticatedUser() {

        when(userRepository.findByEmail(SUPPLIER_EMAIL))
                .thenReturn(Optional.of(supplierUser));

        when(supplierUser.isEnabled())
                .thenReturn(true);
    }

    // Configure an authenticated supplier
    private void mockAuthenticatedSupplier() {

        mockAuthenticatedUser();

        when(supplierUser.getRole())
                .thenReturn(UserRole.SUPPLIER);

        when(supplierUser.getOrganization())
                .thenReturn(supplierOrganization);
    }

    // Test 1: Approve a pending relationship
    @Test
    void approvePendingRelationshipShouldSetStatusToActive() {

        mockAuthenticatedSupplier();

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        when(relationshipRepository.save(relationship))
                .thenReturn(relationship);

        BuyerSupplierRelationship result =
                relationshipService.approveRelationship(
                        1L,
                        SUPPLIER_EMAIL
                );

        assertEquals(
                RelationshipStatus.ACTIVE,
                result.getStatus()
        );

        verify(relationshipRepository).save(relationship);
    }

    // Test 2: Reject a pending relationship
    @Test
    void rejectPendingRelationshipShouldSetStatusToRejected() {

        mockAuthenticatedSupplier();

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        when(relationshipRepository.save(relationship))
                .thenReturn(relationship);

        BuyerSupplierRelationship result =
                relationshipService.rejectRelationship(
                        1L,
                        SUPPLIER_EMAIL
                );

        assertEquals(
                RelationshipStatus.REJECTED,
                result.getStatus()
        );

        verify(relationshipRepository).save(relationship);
    }

    // Test 3: Approving an already active relationship should fail
    @Test
    void approvingActiveRelationshipShouldThrowConflict() {

        mockAuthenticatedSupplier();

        relationship.setStatus(RelationshipStatus.ACTIVE);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> relationshipService.approveRelationship(
                        1L,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(relationshipRepository, never()).save(any());
    }

    // Test 4: Rejecting an already rejected relationship should fail
    @Test
    void rejectingRejectedRelationshipShouldThrowConflict() {

        mockAuthenticatedSupplier();

        relationship.setStatus(RelationshipStatus.REJECTED);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> relationshipService.rejectRelationship(
                        1L,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(relationshipRepository, never()).save(any());
    }

    // Test 5: Approving a nonexistent relationship should return 404
    @Test
    void approvingNonexistentRelationshipShouldThrowNotFound() {

        mockAuthenticatedUser();

        when(relationshipRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> relationshipService.approveRelationship(
                        99L,
                        SUPPLIER_EMAIL
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(relationshipRepository, never()).save(any());
    }
}