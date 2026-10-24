package com.tradeflow.api.organization;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tradeflow.api.config.SecurityConfig;


import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationController.class)
@Import({
        SecurityConfig.class,
        OrganizationControllerIntegrationTest.JwtTestConfiguration.class
})
class OrganizationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private OrganizationService organizationService;

    private static final String BASE_URL = "/api/v1/organizations";

    private String createToken(String email, String role) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("tradeflow-api")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1800))
                .subject(email)
                .claim("userId", 1L)
                .claim("role", role)
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    private String buyerToken() {
        return createToken("buyer@example.com", "BUYER");
    }

    private String supplierToken() {
        return createToken("supplier@example.com", "SUPPLIER");
    }

    private String adminToken() {
        return createToken("admin@example.com", "ADMIN");
    }

    private String organizationRequest(
            String name,
            String email
    ) {
        return """
                {
                    "name": "%s",
                    "businessEmail": "%s",
                    "phone": "9876543210",
                    "address": "Pune, Maharashtra"
                }
                """.formatted(name, email);
    }

    // --------------------------------------------------
    // ORGANIZATION ONBOARDING
    // --------------------------------------------------

    @Test
    void buyerCanOnboardOrganization() throws Exception {

        when(organizationService.onboardOrganization(
                any(OrganizationOnboardRequest.class),
                eq("buyer@example.com")
        )).thenReturn(
                new OrganizationOnboardResponse(
                        "Organization created successfully",
                        1L,
                        "Test Buyer Organization"
                )
        );

        mockMvc.perform(post(BASE_URL + "/onboard")
                        .header(
                                "Authorization",
                                "Bearer " + buyerToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationRequest(
                                "Test Buyer Organization",
                                "buyer-business@example.com"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message")
                        .value("Organization created successfully"))
                .andExpect(jsonPath("$.organizationId").value(1))
                .andExpect(jsonPath("$.organizationName")
                        .value("Test Buyer Organization"));
    }

    @Test
    void supplierCanOnboardOrganization() throws Exception {

        when(organizationService.onboardOrganization(
                any(OrganizationOnboardRequest.class),
                eq("supplier@example.com")
        )).thenReturn(
                new OrganizationOnboardResponse(
                        "Organization created successfully",
                        2L,
                        "Test Supplier Organization"
                )
        );

        mockMvc.perform(post(BASE_URL + "/onboard")
                        .header(
                                "Authorization",
                                "Bearer " + supplierToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationRequest(
                                "Test Supplier Organization",
                                "supplier-business@example.com"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(2))
                .andExpect(jsonPath("$.organizationName")
                        .value("Test Supplier Organization"));
    }

    @Test
    void adminCannotOnboardOrganization() throws Exception {

        mockMvc.perform(post(BASE_URL + "/onboard")
                        .header(
                                "Authorization",
                                "Bearer " + adminToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationRequest(
                                "Admin Organization",
                                "admin-business@example.com"
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void onboardingWithoutAuthenticationReturnsUnauthorized()
            throws Exception {

        mockMvc.perform(post(BASE_URL + "/onboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationRequest(
                                "Test Organization",
                                "business@example.com"
                        )))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onboardingRejectsMissingRequiredFields() throws Exception {

        mockMvc.perform(post(BASE_URL + "/onboard")
                        .header(
                                "Authorization",
                                "Bearer " + buyerToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "",
                                    "businessEmail": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onboardingRejectsInvalidBusinessEmail() throws Exception {

        mockMvc.perform(post(BASE_URL + "/onboard")
                        .header(
                                "Authorization",
                                "Bearer " + buyerToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationRequest(
                                "Test Organization",
                                "invalid-email"
                        )))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onboardingRejectsOrganizationNameExceedingLimit()
            throws Exception {

        String longName = "A".repeat(151);

        mockMvc.perform(post(BASE_URL + "/onboard")
                        .header(
                                "Authorization",
                                "Bearer " + buyerToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationRequest(
                                longName,
                                "business@example.com"
                        )))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------
    // ORGANIZATION LIST ACCESS
    // --------------------------------------------------

    @Test
    void buyerCannotRetrieveAllOrganizations() throws Exception {

        mockMvc.perform(get(BASE_URL)
                        .header(
                                "Authorization",
                                "Bearer " + buyerToken()
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void supplierCannotRetrieveAllOrganizations() throws Exception {

        mockMvc.perform(get(BASE_URL)
                        .header(
                                "Authorization",
                                "Bearer " + supplierToken()
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanRetrieveAllOrganizations() throws Exception {

        when(organizationService.getAllOrganizations(
                "admin@example.com"
        )).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL)
                        .header(
                                "Authorization",
                                "Bearer " + adminToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listingOrganizationsWithoutAuthenticationReturnsUnauthorized()
            throws Exception {

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------
    // ADMIN-ONLY ORGANIZATION CREATION
    // --------------------------------------------------

    @Test
    void buyerCannotCreateOrganizationThroughAdminEndpoint()
            throws Exception {

        mockMvc.perform(post(BASE_URL)
                        .header(
                                "Authorization",
                                "Bearer " + buyerToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationRequest(
                                "Test Organization",
                                "business@example.com"
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateOrganization() throws Exception {

        when(organizationService.createOrganization(
                any(OrganizationRequest.class)
        )).thenAnswer(invocation -> {

            OrganizationRequest request = invocation.getArgument(0);

            Organization organization = new Organization();
            organization.setName(request.name());
            organization.setBusinessEmail(request.businessEmail());
            organization.setPhone(request.phone());
            organization.setAddress(request.address());

            return organization;
        });

        mockMvc.perform(post(BASE_URL)
                        .header(
                                "Authorization",
                                "Bearer " + adminToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(organizationRequest(
                                "Admin Created Organization",
                                "admin-created@example.com"
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name")
                        .value("Admin Created Organization"))
                .andExpect(jsonPath("$.businessEmail")
                        .value("admin-created@example.com"));
    }

    // --------------------------------------------------
    // ORGANIZATION ID VALIDATION
    // --------------------------------------------------

    @Test
    void invalidOrganizationIdIsRejected() throws Exception {

        /*
         * @Positive on the controller's path variable currently
         * throws ConstraintViolationException.
         *
         * Until centralized validation exception handling is added,
         * this test should be handled separately from HTTP response
         * assertions.
         */
        org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.servlet.ServletException.class,
                () -> mockMvc.perform(
                        get(BASE_URL + "/0")
                                .header(
                                        "Authorization",
                                        "Bearer " + buyerToken()
                                )
                )
        );
    }

    // --------------------------------------------------
    // TEST JWT CONFIGURATION
    // --------------------------------------------------

    @TestConfiguration
    static class JwtTestConfiguration {

        private static final String TEST_SECRET =
                Base64.getEncoder().encodeToString(
                        "tradeflow-integration-test-secret-key-32"
                                .getBytes(StandardCharsets.UTF_8)
                );

        @Bean
        SecretKey jwtSecretKey() {

            byte[] secretBytes =
                    Base64.getDecoder().decode(TEST_SECRET);

            return new SecretKeySpec(
                    secretBytes,
                    "HmacSHA256"
            );
        }

        @Bean
        JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {

            return new NimbusJwtEncoder(
                    new ImmutableSecret<>(jwtSecretKey)
            );
        }
    }
}
