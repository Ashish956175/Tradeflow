package com.tradeflow.api.supplier;

import com.tradeflow.api.membership.OrganizationMembership;
import com.tradeflow.api.organization.Organization;
import com.tradeflow.api.organization.OrganizationCapability;
import com.tradeflow.api.organization.OrganizationRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private MembershipAuthorizationService authorizationService;

    @InjectMocks
    private SupplierService supplierService;

    private Organization organization;
    private OrganizationMembership membership;
    private Supplier supplier;
    private User supplierUser;
    private User adminUser;

    private static final Long ORGANIZATION_ID = 10L;
    private static final Long SUPPLIER_ID = 30L;

    private static final String SUPPLIER_EMAIL = "supplier@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";

    private SupplierRequest request;

    @BeforeEach
    void setUp() {

        organization = new Organization();
        ReflectionTestUtils.setField(
                organization, "id", ORGANIZATION_ID
        );

        organization.setName("Supplier Organization");

        membership = mock(OrganizationMembership.class);

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

        supplier = new Supplier();
        ReflectionTestUtils.setField(
                supplier, "id", SUPPLIER_ID
        );
        supplier.setOrganization(organization);
        supplier.setName("Industrial Supplies");
        supplier.setEmail("contact@example.com");

        request = new SupplierRequest(
                "  Industrial Supplies  ",
                "  CONTACT@EXAMPLE.COM  ",
                "  9876543210  ",
                "  Pune  "
        );
    }

    private void authenticate(String email, User user) {

        when(authorizationService.getAuthenticatedUser(email))
                .thenReturn(user);
    }

    private void authorizeSupplierMembership(String email) {

        when(authorizationService.requireOrganizationCapability(
                email,
                ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenReturn(membership);

        when(membership.getOrganization())
                .thenReturn(organization);
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

    // ---------------------------------------------------------
    // Supplier creation
    // ---------------------------------------------------------

    @Test
    void createSupplier_withSupplierCapability_shouldSucceed() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplierMembership(SUPPLIER_EMAIL);

        when(supplierRepository.existsByOrganization_IdAndEmail(
                ORGANIZATION_ID,
                "contact@example.com"
        )).thenReturn(false);

        when(supplierRepository.save(any(Supplier.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Supplier result = supplierService.createSupplier(
                request,
                SUPPLIER_EMAIL,
                ORGANIZATION_ID
        );

        assertNotNull(result);
        assertEquals(organization, result.getOrganization());
        assertEquals("Industrial Supplies", result.getName());
        assertEquals("contact@example.com", result.getEmail());
        assertEquals("9876543210", result.getPhone());
        assertEquals("Pune", result.getAddress());

        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    void createSupplier_withDuplicateEmail_shouldReturnConflict() {

        authenticate(SUPPLIER_EMAIL, supplierUser);
        authorizeSupplierMembership(SUPPLIER_EMAIL);

        when(supplierRepository.existsByOrganization_IdAndEmail(
                ORGANIZATION_ID,
                "contact@example.com"
        )).thenReturn(true);

        assertStatus(
                HttpStatus.CONFLICT,
                () -> supplierService.createSupplier(
                        request,
                        SUPPLIER_EMAIL,
                        ORGANIZATION_ID
                )
        );

        verify(supplierRepository, never()).save(any());
    }

    @Test
    void createSupplier_withoutSupplierCapability_shouldBeRejected() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Organization capability is not available"
        ));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> supplierService.createSupplier(
                        request,
                        SUPPLIER_EMAIL,
                        ORGANIZATION_ID
                )
        );

        verify(supplierRepository, never()).save(any());
    }

    @Test
    void createSupplier_asPlatformAdmin_shouldBeForbidden() {

        authenticate(ADMIN_EMAIL, adminUser);

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> supplierService.createSupplier(
                        request,
                        ADMIN_EMAIL,
                        ORGANIZATION_ID
                )
        );

        verify(
                authorizationService,
                never()
        ).requireOrganizationCapability(
                any(),
                any(),
                any()
        );

        verifyNoInteractions(supplierRepository);
    }

    // ---------------------------------------------------------
    // Organization supplier listing
    // ---------------------------------------------------------

    @Test
    void getSuppliersByOrganization_withSupplierCapability_shouldSucceed() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenReturn(membership);

        when(organizationRepository.existsById(ORGANIZATION_ID))
                .thenReturn(true);

        when(supplierRepository.findAllByOrganization_Id(ORGANIZATION_ID))
                .thenReturn(List.of(supplier));

        List<Supplier> result =
                supplierService.getSuppliersByOrganization(
                        ORGANIZATION_ID,
                        SUPPLIER_EMAIL
                );

        assertEquals(1, result.size());
        assertEquals(supplier, result.get(0));
    }

    @Test
    void getSuppliersByOrganization_asPlatformAdmin_shouldSucceed() {

        authenticate(ADMIN_EMAIL, adminUser);

        when(organizationRepository.existsById(ORGANIZATION_ID))
                .thenReturn(true);

        when(supplierRepository.findAllByOrganization_Id(ORGANIZATION_ID))
                .thenReturn(List.of(supplier));

        List<Supplier> result =
                supplierService.getSuppliersByOrganization(
                        ORGANIZATION_ID,
                        ADMIN_EMAIL
                );

        assertEquals(1, result.size());

        verify(
                authorizationService,
                never()
        ).requireOrganizationCapability(
                any(),
                any(),
                any()
        );
    }

    @Test
    void getSuppliersByOrganization_withoutSupplierCapability_shouldBeForbidden() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN
        ));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> supplierService.getSuppliersByOrganization(
                        ORGANIZATION_ID,
                        SUPPLIER_EMAIL
                )
        );

        verifyNoInteractions(supplierRepository);
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void getSuppliersByOrganization_withUnknownOrganization_shouldReturnNotFound() {

        authenticate(ADMIN_EMAIL, adminUser);

        when(organizationRepository.existsById(ORGANIZATION_ID))
                .thenReturn(false);

        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> supplierService.getSuppliersByOrganization(
                        ORGANIZATION_ID,
                        ADMIN_EMAIL
                )
        );

        verify(
                supplierRepository,
                never()
        ).findAllByOrganization_Id(any());
    }

    // ---------------------------------------------------------
    // Individual supplier retrieval
    // ---------------------------------------------------------

    @Test
    void getSupplierById_withSupplierCapability_shouldSucceed() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenReturn(membership);

        Supplier result = supplierService.getSupplierById(
                SUPPLIER_ID,
                SUPPLIER_EMAIL
        );

        assertEquals(supplier, result);
    }

    @Test
    void getSupplierById_asPlatformAdmin_shouldSucceed() {

        authenticate(ADMIN_EMAIL, adminUser);

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        Supplier result = supplierService.getSupplierById(
                SUPPLIER_ID,
                ADMIN_EMAIL
        );

        assertEquals(supplier, result);

        verify(
                authorizationService,
                never()
        ).requireOrganizationCapability(
                any(),
                any(),
                any()
        );
    }

    @Test
    void getSupplierById_withoutSupplierCapability_shouldBeForbidden() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                ORGANIZATION_ID,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN
        ));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> supplierService.getSupplierById(
                        SUPPLIER_ID,
                        SUPPLIER_EMAIL
                )
        );
    }

    @Test
    void getSupplierById_withUnknownSupplier_shouldReturnNotFound() {

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(supplierRepository.findById(SUPPLIER_ID))
                .thenReturn(Optional.empty());

        assertStatus(
                HttpStatus.NOT_FOUND,
                () -> supplierService.getSupplierById(
                        SUPPLIER_ID,
                        SUPPLIER_EMAIL
                )
        );

        verify(
                authorizationService,
                never()
        ).requireOrganizationCapability(
                any(),
                any(),
                any()
        );
    }

    @Test
    void createSupplier_forDifferentOrganization_shouldBeForbidden() {

        Long differentOrganizationId = 20L;

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                differentOrganizationId,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Organization membership not found"
        ));

        assertStatus(
                HttpStatus.FORBIDDEN,
                () -> supplierService.createSupplier(
                        request,
                        SUPPLIER_EMAIL,
                        differentOrganizationId
                )
        );

        verify(supplierRepository, never()).save(any());
    }

    @Test
    void getSupplierById_fromDifferentOrganization_shouldBeForbidden() {

        Long differentOrganizationId = 20L;

        Organization differentOrganization = new Organization();
        ReflectionTestUtils.setField(
                differentOrganization,
                "id",
                differentOrganizationId
        );

        Supplier supplierFromDifferentOrganization = new Supplier();
        ReflectionTestUtils.setField(
                supplierFromDifferentOrganization,
                "id",
                40L
        );
        supplierFromDifferentOrganization.setOrganization(
                differentOrganization
        );

        authenticate(SUPPLIER_EMAIL, supplierUser);

        when(supplierRepository.findById(40L))
                .thenReturn(Optional.of(supplierFromDifferentOrganization));

        when(authorizationService.requireOrganizationCapability(
                SUPPLIER_EMAIL,
                differentOrganizationId,
                OrganizationCapability.SUPPLIER
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Organization membership not found"
        ));


        assertStatus(

                HttpStatus.FORBIDDEN,
                () -> supplierService.getSupplierById(
                        40L,
                        SUPPLIER_EMAIL
                )

        );

    }

}
