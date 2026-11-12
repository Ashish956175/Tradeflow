package com.tradeflow.api.quotation;

import com.tradeflow.api.membership.OrganizationMembership;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.procurement.ProcurementRequest;
import com.tradeflow.api.procurement.ProcurementRequestRepository;
import com.tradeflow.api.procurement.ProcurementStatus;
import com.tradeflow.api.quotation.*;
import com.tradeflow.api.relationship.BuyerSupplierRelationship;
import com.tradeflow.api.relationship.BuyerSupplierRelationshipRepository;
import com.tradeflow.api.relationship.RelationshipStatus;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotationServiceTest {

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private ProcurementRequestRepository procurementRequestRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private BuyerSupplierRelationshipRepository relationshipRepository;

    @Mock
    private MembershipAuthorizationService authorizationService;

    @InjectMocks
    private QuotationService quotationService;

    private Organization buyerOrganization;
    private Organization supplierOrganization;
    private Organization otherSupplierOrganization;

    private Supplier supplier;
    private Supplier otherSupplier;

    private ProcurementRequest procurementRequest;
    private BuyerSupplierRelationship relationship;
    private Quotation quotation;

    private User supplierUser;
    private User buyerUser;
    private User adminUser;

    private static final Long BUYER_ORGANIZATION_ID = 20L;
    private static final Long SUPPLIER_ORGANIZATION_ID = 30L;
    private static final Long OTHER_SUPPLIER_ORGANIZATION_ID = 40L;

    private static final Long PROCUREMENT_REQUEST_ID = 50L;
    private static final Long SUPPLIER_ID = 60L;
    private static final Long OTHER_SUPPLIER_ID = 61L;
    private static final Long QUOTATION_ID = 70L;

    private static final String BUYER_EMAIL = "buyer@example.com";
    private static final String SUPPLIER_EMAIL = "supplier@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";

    private QuotationRequest request;

    @BeforeEach
    void setUp() {

        buyerOrganization = mock(Organization.class);
        supplierOrganization = mock(Organization.class);
        otherSupplierOrganization = mock(Organization.class);

        supplier = mock(Supplier.class);
        otherSupplier = mock(Supplier.class);

        procurementRequest = mock(ProcurementRequest.class);
        relationship = mock(BuyerSupplierRelationship.class);

        supplierUser = mock(User.class);
        buyerUser = mock(User.class);
        adminUser = mock(User.class);

        request = new QuotationRequest(
                PROCUREMENT_REQUEST_ID,
                SUPPLIER_ID,
                new BigDecimal("120.00"),
                100,
                LocalDate.now().plusDays(10)
        );

        quotation = new Quotation(
                procurementRequest,
                supplier,
                new BigDecimal("120.00"),
                100,
                LocalDate.now().plusDays(10)
        );

        ReflectionTestUtils.setField(
                quotation,
                "id",
                QUOTATION_ID
        );
    }

    // ---------------------------------------------------------
    // Narrow stubbing helpers
    // ---------------------------------------------------------

    private void stubSupplierOrganization() {
        when(supplier.getOrganization()).thenReturn(supplierOrganization);
        when(supplierOrganization.getId()).thenReturn(SUPPLIER_ORGANIZATION_ID);
    }

    private void stubSupplierId() {
        when(supplier.getId()).thenReturn(SUPPLIER_ID);
    }

    private void stubOtherSupplierOrganization() {
        when(otherSupplier.getOrganization()).thenReturn(otherSupplierOrganization);
        when(otherSupplierOrganization.getId())
                .thenReturn(OTHER_SUPPLIER_ORGANIZATION_ID);
    }

    private void stubBuyerOrganization() {
        when(procurementRequest.getBuyerOrganization())
                .thenReturn(buyerOrganization);
        when(buyerOrganization.getId()).thenReturn(BUYER_ORGANIZATION_ID);
    }

    private void stubProcurementRequestId() {
        when(procurementRequest.getId()).thenReturn(PROCUREMENT_REQUEST_ID);
    }

    private void stubOpenProcurementRequestStatus() {
        when(procurementRequest.getStatus()).thenReturn(ProcurementStatus.OPEN);
    }

    private void stubActiveRelationship() {
        when(relationship.getStatus()).thenReturn(RelationshipStatus.ACTIVE);
    }

    private void stubAdminUser() {
        when(adminUser.getRole()).thenReturn(UserRole.ADMIN);
    }

    private void authenticate(String email, User user) {
        when(authorizationService.getAuthenticatedUser(email))
                .thenReturn(user);
    }

    private void authorizeSupplier(String email) {
        when(authorizationService.requireOrganizationCapability(
                email,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenReturn(mock(OrganizationMembership.class));
    }

    private void authorizeBuyer(String email) {
        when(authorizationService.requireOrganizationCapability(
                email,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenReturn(mock(OrganizationMembership.class));
    }

    private void assertStatus(HttpStatus expectedStatus, Runnable operation) {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                operation::run
        );

        assertEquals(
                expectedStatus.value(),
                exception.getStatusCode().value()
        );
    }

    // ---------------------------------------------------------
    // Quotation submission
    // ---------------------------------------------------------

    @Test
    void createQuotation_withValidSupplierAndRelationship_shouldSucceed() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplier(SUPPLIER_EMAIL);

        stubSupplierOrganization();
        stubSupplierId();
        stubBuyerOrganization();
        stubProcurementRequestId();
        stubOpenProcurementRequestStatus();
        stubActiveRelationship();

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        when(procurementRequestRepository.findById(PROCUREMENT_REQUEST_ID))
                .thenReturn(Optional.of(procurementRequest));

        when(relationshipRepository
                .findByBuyerOrganization_IdAndSupplier_Id(
                        BUYER_ORGANIZATION_ID,
                        SUPPLIER_ID
                )).thenReturn(Optional.of(relationship));

        when(quotationRepository
                .existsByProcurementRequest_IdAndSupplier_Id(
                        PROCUREMENT_REQUEST_ID,
                        SUPPLIER_ID
                )).thenReturn(false);

        when(quotationRepository.save(any(Quotation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Quotation result = quotationService.createQuotation(
                request,
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID
        );

        assertNotNull(result);
        assertSame(procurementRequest, result.getProcurementRequest());
        assertSame(supplier, result.getSupplier());
        assertEquals(QuotationStatus.SUBMITTED, result.getStatus());
        assertEquals(new BigDecimal("120.00"), result.getOfferedUnitPrice());
        assertEquals(100, result.getOfferedQuantity());

        verify(quotationRepository).save(any(Quotation.class));
    }

    @Test
    void createQuotation_asPlatformAdmin_shouldBeForbidden() {

        authenticate(ADMIN_EMAIL, adminUser);
        stubAdminUser();

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> quotationService.createQuotation(
                        request,
                        ADMIN_EMAIL,
                        SUPPLIER_ORGANIZATION_ID
                )
        );

        verifyNoInteractions(supplierRepository);
        verifyNoInteractions(procurementRequestRepository);
        verifyNoInteractions(quotationRepository);
    }

    @Test
    void createQuotation_withoutSupplierCapability_shouldBeForbidden() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> quotationService.createQuotation(
                        request,
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID
                )
        );

        verifyNoInteractions(supplierRepository);
        verifyNoInteractions(quotationRepository);
    }

    @Test
    void createQuotation_withUnknownSupplier_shouldReturnNotFound() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplier(SUPPLIER_EMAIL);

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> quotationService.createQuotation(
                        request,
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID
                )
        );

        verifyNoInteractions(procurementRequestRepository);
        verify(quotationRepository, never()).save(any());
    }

    @Test
    void createQuotation_withSupplierFromAnotherOrganization_shouldBeForbidden() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplier(SUPPLIER_EMAIL);
        stubOtherSupplierOrganization();

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.of(otherSupplier));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> quotationService.createQuotation(
                        request,
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID
                )
        );

        verifyNoInteractions(procurementRequestRepository);
        verify(quotationRepository, never()).save(any());
    }

    @Test
    void createQuotation_withUnknownProcurementRequest_shouldReturnNotFound() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplier(SUPPLIER_EMAIL);
        stubSupplierOrganization();

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        when(procurementRequestRepository.findById(PROCUREMENT_REQUEST_ID))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> quotationService.createQuotation(
                        request,
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID
                )
        );

        verify(quotationRepository, never()).save(any());
    }

    @Test
    void createQuotation_forNonOpenProcurementRequest_shouldReturnConflict() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplier(SUPPLIER_EMAIL);
        stubSupplierOrganization();

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        when(procurementRequestRepository.findById(PROCUREMENT_REQUEST_ID))
                .thenReturn(Optional.of(procurementRequest));

        when(procurementRequest.getStatus())
                .thenReturn(ProcurementStatus.ACCEPTED);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> quotationService.createQuotation(
                        request,
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID
                )
        );

        verifyNoInteractions(relationshipRepository);
        verify(quotationRepository, never()).save(any());
    }

    @Test
    void createQuotation_withoutRelationship_shouldBeForbidden() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplier(SUPPLIER_EMAIL);

        stubSupplierOrganization();
        stubSupplierId();
        stubBuyerOrganization();
        stubOpenProcurementRequestStatus();

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        when(procurementRequestRepository.findById(PROCUREMENT_REQUEST_ID))
                .thenReturn(Optional.of(procurementRequest));

        when(relationshipRepository
                .findByBuyerOrganization_IdAndSupplier_Id(
                        BUYER_ORGANIZATION_ID,
                        SUPPLIER_ID
                )).thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> quotationService.createQuotation(
                        request,
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID
                )
        );

        verify(quotationRepository, never()).save(any());
    }

    @Test
    void createQuotation_withInactiveRelationship_shouldBeForbidden() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplier(SUPPLIER_EMAIL);

        stubSupplierOrganization();
        stubSupplierId();
        stubBuyerOrganization();
        stubOpenProcurementRequestStatus();

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        when(procurementRequestRepository.findById(PROCUREMENT_REQUEST_ID))
                .thenReturn(Optional.of(procurementRequest));

        when(relationshipRepository
                .findByBuyerOrganization_IdAndSupplier_Id(
                        BUYER_ORGANIZATION_ID,
                        SUPPLIER_ID
                )).thenReturn(Optional.of(relationship));

        when(relationship.getStatus())
                .thenReturn(RelationshipStatus.PENDING);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> quotationService.createQuotation(
                        request,
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID
                )
        );

        verify(quotationRepository, never()).save(any());
    }

    @Test
    void createQuotation_whenSupplierAlreadySubmitted_shouldReturnConflict() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplier(SUPPLIER_EMAIL);

        stubSupplierOrganization();
        stubSupplierId();
        stubBuyerOrganization();
        stubProcurementRequestId();
        stubOpenProcurementRequestStatus();
        stubActiveRelationship();

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        when(procurementRequestRepository.findById(PROCUREMENT_REQUEST_ID))
                .thenReturn(Optional.of(procurementRequest));

        when(relationshipRepository
                .findByBuyerOrganization_IdAndSupplier_Id(
                        BUYER_ORGANIZATION_ID,
                        SUPPLIER_ID
                )).thenReturn(Optional.of(relationship));

        when(quotationRepository
                .existsByProcurementRequest_IdAndSupplier_Id(
                        PROCUREMENT_REQUEST_ID,
                        SUPPLIER_ID
                )).thenReturn(true);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> quotationService.createQuotation(
                        request,
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID
                )
        );

        verify(quotationRepository, never()).save(any());
    }

    // ---------------------------------------------------------
    // Quotation listing
    // ---------------------------------------------------------

    @Test
    void getQuotationsByProcurementRequest_asBuyer_shouldReturnAllQuotations() {

        authenticate(BUYER_EMAIL, buyerUser);
        stubBuyerOrganization();

        List<Quotation> expected = List.of(quotation);

        when(procurementRequestRepository.findById(PROCUREMENT_REQUEST_ID))
                .thenReturn(Optional.of(procurementRequest));

        authorizeBuyer(BUYER_EMAIL);

        when(quotationRepository
                .findByProcurementRequest_Id(PROCUREMENT_REQUEST_ID))
                .thenReturn(expected);

        List<Quotation> result =
                quotationService.getQuotationsByProcurementRequest(
                        PROCUREMENT_REQUEST_ID,
                        BUYER_EMAIL,
                        BUYER_ORGANIZATION_ID
                );

        assertEquals(expected, result);
    }

    @Test
    void getQuotationsByProcurementRequest_asSupplier_shouldReturnOnlyOwnOrganizationQuotations() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplier(SUPPLIER_EMAIL);

        stubSupplierOrganization();
        stubSupplierId();
        stubOtherSupplierOrganization();
        stubBuyerOrganization();
        stubActiveRelationship();




        Quotation otherQuotation = new Quotation(
                procurementRequest,
                otherSupplier,
                new BigDecimal("125.00"),
                80,
                LocalDate.now().plusDays(12)
        );

        when(procurementRequestRepository.findById(PROCUREMENT_REQUEST_ID))
                .thenReturn(Optional.of(procurementRequest));

        when(supplierRepository.findAllByOrganization_Id(
                SUPPLIER_ORGANIZATION_ID
        )).thenReturn(List.of(supplier));

        when(relationshipRepository
                .findByBuyerOrganization_IdAndSupplier_Id(
                        BUYER_ORGANIZATION_ID,
                        SUPPLIER_ID
                )).thenReturn(Optional.of(relationship));

        when(quotationRepository
                .findByProcurementRequest_Id(PROCUREMENT_REQUEST_ID))
                .thenReturn(List.of(quotation, otherQuotation));

        List<Quotation> result =
                quotationService.getQuotationsByProcurementRequest(
                        PROCUREMENT_REQUEST_ID,
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID
                );

        assertEquals(1, result.size());
        assertSame(quotation, result.get(0));
    }

    @Test
    void getQuotationsByProcurementRequest_asPlatformAdmin_shouldReturnAllQuotations() {

        authenticate(ADMIN_EMAIL, adminUser);
        stubAdminUser();

        List<Quotation> expected = List.of(quotation);

        when(procurementRequestRepository.findById(PROCUREMENT_REQUEST_ID))
                .thenReturn(Optional.of(procurementRequest));

        when(quotationRepository
                .findByProcurementRequest_Id(PROCUREMENT_REQUEST_ID))
                .thenReturn(expected);

        List<Quotation> result =
                quotationService.getQuotationsByProcurementRequest(
                        PROCUREMENT_REQUEST_ID,
                        ADMIN_EMAIL,
                        BUYER_ORGANIZATION_ID
                );

        assertEquals(expected, result);

        verifyNoInteractions(supplierRepository);
        verifyNoInteractions(relationshipRepository);
    }

    @Test
    void getQuotationsByProcurementRequest_supplierWithoutActiveRelationship_shouldBeForbidden() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplier(SUPPLIER_EMAIL);

        stubSupplierId();
        stubBuyerOrganization();

        when(procurementRequestRepository.findById(PROCUREMENT_REQUEST_ID))
                .thenReturn(Optional.of(procurementRequest));

        when(supplierRepository.findAllByOrganization_Id(
                SUPPLIER_ORGANIZATION_ID
        )).thenReturn(List.of(supplier));

        when(relationshipRepository
                .findByBuyerOrganization_IdAndSupplier_Id(
                        BUYER_ORGANIZATION_ID,
                        SUPPLIER_ID
                )).thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> quotationService.getQuotationsByProcurementRequest(
                        PROCUREMENT_REQUEST_ID,
                        SUPPLIER_EMAIL,
                        SUPPLIER_ORGANIZATION_ID
                )
        );

        verify(quotationRepository, never())
                .findByProcurementRequest_Id(any());
    }

    // ---------------------------------------------------------
    // Individual quotation retrieval
    // ---------------------------------------------------------

    @Test
    void getQuotationById_asPlatformAdmin_shouldSucceed() {

        authenticate(ADMIN_EMAIL, adminUser);
        stubAdminUser();

        when(quotationRepository.findById(QUOTATION_ID))
                .thenReturn(Optional.of(quotation));

        Quotation result = quotationService.getQuotationById(
                QUOTATION_ID,
                ADMIN_EMAIL
        );

        assertSame(quotation, result);

        verify(authorizationService, never())
                .requireOrganizationCapability(any(), any(), any());
    }

    @Test
    void getQuotationById_withBuyerCapability_shouldSucceed() {

        authenticate(BUYER_EMAIL, buyerUser);

        stubBuyerOrganization();
        stubSupplierOrganization();

        when(quotationRepository.findById(QUOTATION_ID))
                .thenReturn(Optional.of(quotation));

        authorizeBuyer(BUYER_EMAIL);

        Quotation result = quotationService.getQuotationById(
                QUOTATION_ID,
                BUYER_EMAIL
        );

        assertSame(quotation, result);

        verify(authorizationService).requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );
    }

    @Test
    void getQuotationById_withSupplierCapability_shouldSucceed() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        stubBuyerOrganization();
        stubSupplierOrganization();

        when(quotationRepository.findById(QUOTATION_ID))
                .thenReturn(Optional.of(quotation));

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        authorizeSupplier(SUPPLIER_EMAIL);

        Quotation result = quotationService.getQuotationById(
                QUOTATION_ID,
                SUPPLIER_EMAIL
        );

        assertSame(quotation, result);
    }

    @Test
    void getQuotationById_withoutBuyerOrSupplierCapability_shouldBeForbidden() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        stubBuyerOrganization();
        stubSupplierOrganization();

        when(quotationRepository.findById(QUOTATION_ID))
                .thenReturn(Optional.of(quotation));

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                SUPPLIER_ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> quotationService.getQuotationById(
                        QUOTATION_ID,
                        SUPPLIER_EMAIL
                )
        );
    }

    // ---------------------------------------------------------
    // Accept quotation
    // ---------------------------------------------------------

    @Test
    void acceptQuotation_withBuyerCapability_shouldSucceed() {

        authenticate(BUYER_EMAIL, buyerUser);
        stubBuyerOrganization();

        when(quotationRepository.findById(QUOTATION_ID))
                .thenReturn(Optional.of(quotation));

        authorizeBuyer(BUYER_EMAIL);

        when(quotationRepository.save(quotation))
                .thenReturn(quotation);

        Quotation result = quotationService.acceptQuotation(
                QUOTATION_ID,
                BUYER_EMAIL
        );

        assertSame(quotation, result);
        assertEquals(QuotationStatus.ACCEPTED, result.getStatus());

        verify(procurementRequest).acceptQuotation();
        verify(quotationRepository).save(quotation);
    }

    @Test
    void acceptQuotation_asPlatformAdmin_shouldBeForbidden() {

        authenticate(ADMIN_EMAIL, adminUser);
        stubAdminUser();

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> quotationService.acceptQuotation(
                        QUOTATION_ID,
                        ADMIN_EMAIL
                )
        );

        verifyNoInteractions(quotationRepository);
    }

    @Test
    void acceptQuotation_withoutBuyerCapability_shouldBeForbidden() {

        authenticate(BUYER_EMAIL, buyerUser);
        stubBuyerOrganization();

        when(quotationRepository.findById(QUOTATION_ID))
                .thenReturn(Optional.of(quotation));

        when(authorizationService.requireOrganizationCapability(
                BUYER_EMAIL,
                BUYER_ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> quotationService.acceptQuotation(
                        QUOTATION_ID,
                        BUYER_EMAIL
                )
        );

        verify(quotationRepository, never()).save(any());
        verify(procurementRequest, never()).acceptQuotation();
    }

    @Test
    void acceptQuotation_whenAlreadyRejected_shouldReturnConflict() {

        ReflectionTestUtils.setField(
                quotation,
                "status",
                QuotationStatus.REJECTED
        );

        authenticate(BUYER_EMAIL, buyerUser);
        stubBuyerOrganization();

        when(quotationRepository.findById(QUOTATION_ID))
                .thenReturn(Optional.of(quotation));

        authorizeBuyer(BUYER_EMAIL);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> quotationService.acceptQuotation(
                        QUOTATION_ID,
                        BUYER_EMAIL
                )
        );

        verify(procurementRequest, never()).acceptQuotation();
        verify(quotationRepository, never()).save(any());
    }

    @Test
    void acceptQuotation_whenProcurementRequestCannotBeAccepted_shouldReturnConflict() {

        authenticate(BUYER_EMAIL, buyerUser);
        stubBuyerOrganization();

        when(quotationRepository.findById(QUOTATION_ID))
                .thenReturn(Optional.of(quotation));

        authorizeBuyer(BUYER_EMAIL);

        doThrow(new IllegalStateException("Procurement request cannot be accepted"))
                .when(procurementRequest).acceptQuotation();

        assertStatus(
                HttpStatus.CONFLICT,
                () -> quotationService.acceptQuotation(
                        QUOTATION_ID,
                        BUYER_EMAIL
                )
        );

        verify(quotationRepository, never()).save(any());
    }

    // ---------------------------------------------------------
    // Reject quotation
    // ---------------------------------------------------------

    @Test
    void rejectQuotation_withBuyerCapability_shouldSucceed() {

        authenticate(BUYER_EMAIL, buyerUser);
        stubBuyerOrganization();

        when(quotationRepository.findById(QUOTATION_ID))
                .thenReturn(Optional.of(quotation));

        authorizeBuyer(BUYER_EMAIL);

        when(quotationRepository.save(quotation))
                .thenReturn(quotation);

        Quotation result = quotationService.rejectQuotation(
                QUOTATION_ID,
                BUYER_EMAIL
        );

        assertSame(quotation, result);
        assertEquals(QuotationStatus.REJECTED, result.getStatus());

        verify(quotationRepository).save(quotation);
    }

    @Test
    void rejectQuotation_asPlatformAdmin_shouldBeForbidden() {

        authenticate(ADMIN_EMAIL, adminUser);
        stubAdminUser();

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> quotationService.rejectQuotation(
                        QUOTATION_ID,
                        ADMIN_EMAIL
                )
        );

        verifyNoInteractions(quotationRepository);
    }

    @Test
    void rejectQuotation_whenAlreadyAccepted_shouldReturnConflict() {

        ReflectionTestUtils.setField(
                quotation,
                "status",
                QuotationStatus.ACCEPTED
        );

        authenticate(BUYER_EMAIL, buyerUser);
        stubBuyerOrganization();

        when(quotationRepository.findById(QUOTATION_ID))
                .thenReturn(Optional.of(quotation));

        authorizeBuyer(BUYER_EMAIL);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> quotationService.rejectQuotation(
                        QUOTATION_ID,
                        BUYER_EMAIL
                )
        );

        verify(quotationRepository, never()).save(any());
    }
}
