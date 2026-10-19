package com.tradeflow.api.relationship;

import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationRepository;
import com.tradeflow.api.supplier.Supplier;
import com.tradeflow.api.supplier.SupplierRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationshipServiceTest {

    @Mock
    private BuyerSupplierRelationshipRepository relationshipRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private RelationshipService relationshipService;

    private BuyerSupplierRelationship relationship;

    @BeforeEach
    void setUp() {
        relationship = new BuyerSupplierRelationship();
        relationship.setStatus(RelationshipStatus.PENDING);
    }

    @Test
    void approvePendingRelationshipShouldSetStatusToActive() {

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        when(relationshipRepository.save(relationship))
                .thenReturn(relationship);

        BuyerSupplierRelationship result =
                relationshipService.approveRelationship(1L);

        assertEquals(RelationshipStatus.ACTIVE, result.getStatus());

        verify(relationshipRepository).save(relationship);
    }

    @Test
    void rejectPendingRelationshipShouldSetStatusToRejected() {

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        when(relationshipRepository.save(relationship))
                .thenReturn(relationship);

        BuyerSupplierRelationship result =
                relationshipService.rejectRelationship(1L);

        assertEquals(RelationshipStatus.REJECTED, result.getStatus());

        verify(relationshipRepository).save(relationship);
    }

    @Test
    void approvingActiveRelationshipShouldThrowConflict() {

        relationship.setStatus(RelationshipStatus.ACTIVE);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> relationshipService.approveRelationship(1L)
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(relationshipRepository, never()).save(any());
    }

    @Test
    void rejectingRejectedRelationshipShouldThrowConflict() {

        relationship.setStatus(RelationshipStatus.REJECTED);

        when(relationshipRepository.findById(1L))
                .thenReturn(Optional.of(relationship));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> relationshipService.rejectRelationship(1L)
        );

        assertEquals(409, exception.getStatusCode().value());

        verify(relationshipRepository, never()).save(any());
    }

    @Test
    void approvingNonexistentRelationshipShouldThrowNotFound() {

        when(relationshipRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> relationshipService.approveRelationship(99L)
        );

        assertEquals(404, exception.getStatusCode().value());

        verify(relationshipRepository, never()).save(any());
    }
}