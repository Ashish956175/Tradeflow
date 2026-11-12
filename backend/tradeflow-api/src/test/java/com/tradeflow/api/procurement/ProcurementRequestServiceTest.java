package com.tradeflow.api.procurement;

import com.tradeflow.api.membership.OrganizationMembership;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.product.Product;
import com.tradeflow.api.product.ProductRepository;
import com.tradeflow.api.security.MembershipAuthorizationService;
import com.tradeflow.api.user.User;
import com.tradeflow.api.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcurementRequestServiceTest {

    @Mock
    private ProcurementRequestRepository procurementRequestRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MembershipAuthorizationService authorizationService;

    @InjectMocks
    private ProcurementRequestService procurementRequestService;

    private OrganizationMembership membership;
    private Organization organization;
    private Product product;

    private static final String EMAIL = "buyer@example.com";
    private static final Long ORGANIZATION_ID = 20L;
    private static final Long PRODUCT_ID = 100L;
    private static final Long REQUEST_ID = 50L;

    @BeforeEach
    void setUp() {
        membership = mock(OrganizationMembership.class);
        organization = mock(Organization.class);
        product = mock(Product.class);

        lenient().when(membership.getOrganization())
                .thenReturn(organization);
        lenient().when(organization.getId())
                .thenReturn(ORGANIZATION_ID);
    }

    @Test
    void createProcurementRequest_shouldCreateRequestForAuthorizedOrganization() {
        ProcurementRequestRequest request = new ProcurementRequestRequest(
                PRODUCT_ID,
                5,
                new BigDecimal("12.50"),
                "Pune delivery address",
                LocalDate.now().plusDays(5)
        );

        when(authorizationService.requireOrganizationCapability(
                EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenReturn(membership);

        when(productRepository.findById(PRODUCT_ID))
                .thenReturn(Optional.of(product));

        when(procurementRequestRepository.save(any(ProcurementRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcurementRequest result =
                procurementRequestService.createProcurementRequest(
                        request,
                        EMAIL,
                        ORGANIZATION_ID
                );

        assertNotNull(result);
        assertSame(organization, result.getBuyerOrganization());
        assertSame(product, result.getProduct());

        verify(authorizationService).requireOrganizationCapability(
                EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );
        verify(procurementRequestRepository).save(any(ProcurementRequest.class));
    }

    @Test
    void createProcurementRequest_shouldRejectUserWithoutBuyerCapability() {
        ProcurementRequestRequest request = new ProcurementRequestRequest(
                PRODUCT_ID,
                5,
                new BigDecimal("12.50"),
                "Pune delivery address",
                LocalDate.now().plusDays(5)
        );

        when(authorizationService.requireOrganizationCapability(
                EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Buyer capability required"
        ));

        assertThrows(
                ResponseStatusException.class,
                () -> procurementRequestService.createProcurementRequest(
                        request,
                        EMAIL,
                        ORGANIZATION_ID
                )
        );

        verifyNoInteractions(productRepository);
        verify(procurementRequestRepository, never())
                .save(any(ProcurementRequest.class));
    }

    @Test
    void getMyProcurementRequests_shouldReturnRequestsForSelectedOrganization() {
        List<ProcurementRequest> expectedRequests = List.of(
                mock(ProcurementRequest.class),
                mock(ProcurementRequest.class)
        );

        when(authorizationService.requireOrganizationCapability(
                EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenReturn(membership);

        when(procurementRequestRepository.findByBuyerOrganization_Id(
                ORGANIZATION_ID
        )).thenReturn(expectedRequests);

        List<ProcurementRequest> result =
                procurementRequestService.getMyProcurementRequests(
                        EMAIL,
                        ORGANIZATION_ID
                );

        assertEquals(expectedRequests, result);

        verify(procurementRequestRepository)
                .findByBuyerOrganization_Id(ORGANIZATION_ID);
    }

    @Test
    void getMyProcurementRequests_shouldRejectUserWithoutBuyerCapability() {
        when(authorizationService.requireOrganizationCapability(
                EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Buyer capability required"
        ));

        assertThrows(
                ResponseStatusException.class,
                () -> procurementRequestService.getMyProcurementRequests(
                        EMAIL,
                        ORGANIZATION_ID
                )
        );

        verifyNoInteractions(procurementRequestRepository);
    }

    @Test
    void getProcurementRequestById_shouldAllowPlatformAdmin() {
        User admin = mock(User.class);
        ProcurementRequest request = mock(ProcurementRequest.class);

        when(authorizationService.getAuthenticatedUser(EMAIL))
                .thenReturn(admin);
        when(admin.getRole()).thenReturn(UserRole.ADMIN);
        when(procurementRequestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(request));

        ProcurementRequest result =
                procurementRequestService.getProcurementRequestById(
                        REQUEST_ID,
                        EMAIL
                );

        assertSame(request, result);

        verify(authorizationService, never())
                .requireOrganizationCapability(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    void getProcurementRequestById_shouldAllowMemberWithBuyerCapability() {
        User user = mock(User.class);
        ProcurementRequest request = mock(ProcurementRequest.class);

        when(authorizationService.getAuthenticatedUser(EMAIL))
                .thenReturn(user);
        when(user.getRole()).thenReturn(UserRole.BUYER);
        when(procurementRequestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(request));
        when(request.getBuyerOrganization()).thenReturn(organization);

        when(authorizationService.requireOrganizationCapability(
                EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenReturn(membership);

        ProcurementRequest result =
                procurementRequestService.getProcurementRequestById(
                        REQUEST_ID,
                        EMAIL
                );

        assertSame(request, result);

        verify(authorizationService).requireOrganizationCapability(
                EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.BUYER
        );
    }

    @Test
    void getProcurementRequestById_shouldRejectMemberWithoutBuyerCapability() {
        User user = mock(User.class);
        ProcurementRequest request = mock(ProcurementRequest.class);

        when(authorizationService.getAuthenticatedUser(EMAIL))
                .thenReturn(user);
        when(user.getRole()).thenReturn(UserRole.SUPPLIER);
        when(procurementRequestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(request));
        when(request.getBuyerOrganization()).thenReturn(organization);

        when(authorizationService.requireOrganizationCapability(
                EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.BUYER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Buyer capability required"
        ));

        assertThrows(
                ResponseStatusException.class,
                () -> procurementRequestService.getProcurementRequestById(
                        REQUEST_ID,
                        EMAIL
                )
        );
    }
}
