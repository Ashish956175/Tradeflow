package com.tradeflow.api.relationship;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RelationshipController.class)
@Import({
        SecurityConfig.class,
        RelationshipControllerIntegrationTest.JwtTestConfiguration.class
})
class RelationshipControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private RelationshipService relationshipService;

    private static final String BASE_URL = "/api/v1/relationships";

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

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

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

    private String anotherSupplierToken() {
        return createToken("another-supplier@example.com", "SUPPLIER");
    }

    private String adminToken() {
        return createToken("admin@example.com", "ADMIN");
    }

    // --------------------------------------------------
    // CREATE RELATIONSHIP
    // --------------------------------------------------

    @Test
    void buyerCanCreateRelationship() throws Exception {

        when(relationshipService.createRelationship(
                any(RelationshipRequest.class),
                eq("buyer@example.com")
        )).thenReturn(null);

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + buyerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "supplierId": 30
                                }
                                """))
                .andExpect(status().isCreated());

        verify(relationshipService).createRelationship(
                any(RelationshipRequest.class),
                eq("buyer@example.com")
        );
    }

    @Test
    void createRelationshipWithoutAuthenticationReturnsUnauthorized()
            throws Exception {

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "supplierId": 30
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void supplierCannotCreateRelationship() throws Exception {

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + supplierToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "supplierId": 30
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotCreateRelationship() throws Exception {

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "supplierId": 30
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRelationshipRejectsMissingSupplierId() throws Exception {

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + buyerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRelationshipRejectsNonPositiveSupplierId() throws Exception {

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + buyerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "supplierId": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------
    // GET BUYER RELATIONSHIPS
    // --------------------------------------------------

    @Test
    void buyerCanRequestOwnRelationships() throws Exception {

        when(relationshipService.getBuyerRelationships(
                eq(10L),
                eq("buyer@example.com")
        )).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/buyer/10")
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(relationshipService).getBuyerRelationships(
                10L,
                "buyer@example.com"
        );
    }

    @Test
    void supplierCanRequestTheirRelationships() throws Exception {

        when(relationshipService.getSupplierRelationships(
                eq(30L),
                eq("supplier@example.com")
        )).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/supplier/30")
                        .header("Authorization", "Bearer " + supplierToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanRequestBuyerRelationships() throws Exception {

        when(relationshipService.getBuyerRelationships(
                eq(10L),
                eq("admin@example.com")
        )).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/buyer/10")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void listingRelationshipsWithoutAuthenticationReturnsUnauthorized()
            throws Exception {

        mockMvc.perform(get(BASE_URL + "/buyer/10"))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------
    // APPROVE RELATIONSHIP
    // --------------------------------------------------

    @Test
    void supplierCanApproveRelationship() throws Exception {

        when(relationshipService.approveRelationship(
                1L,
                "supplier@example.com"
        )).thenReturn(null);

        mockMvc.perform(patch(BASE_URL + "/1/approve")
                        .header("Authorization", "Bearer " + supplierToken()))
                .andExpect(status().isOk());

        verify(relationshipService).approveRelationship(
                1L,
                "supplier@example.com"
        );
    }

    @Test
    void buyerCannotApproveRelationship() throws Exception {

        mockMvc.perform(patch(BASE_URL + "/1/approve")
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotApproveRelationship() throws Exception {

        mockMvc.perform(patch(BASE_URL + "/1/approve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanApproveRelationship() throws Exception {

        when(relationshipService.approveRelationship(
                1L,
                "admin@example.com"
        )).thenReturn(null);

        mockMvc.perform(patch(BASE_URL + "/1/approve")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    // --------------------------------------------------
    // REJECT RELATIONSHIP
    // --------------------------------------------------

    @Test
    void supplierCanRejectRelationship() throws Exception {

        when(relationshipService.rejectRelationship(
                1L,
                "supplier@example.com"
        )).thenReturn(null);

        mockMvc.perform(patch(BASE_URL + "/1/reject")
                        .header("Authorization", "Bearer " + supplierToken()))
                .andExpect(status().isOk());

        verify(relationshipService).rejectRelationship(
                1L,
                "supplier@example.com"
        );
    }

    @Test
    void buyerCannotRejectRelationship() throws Exception {

        mockMvc.perform(patch(BASE_URL + "/1/reject")
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isForbidden());
    }

    // --------------------------------------------------
    // PATH VARIABLE VALIDATION
    // --------------------------------------------------

    @Test
    void zeroRelationshipIdIsRejected() throws Exception {

        mockMvc.perform(
                patch(BASE_URL + "/0/approve")
                        .header(
                                "Authorization",
                                "Bearer " + supplierToken()
                        )
        ).andExpect(status().isBadRequest());
    }

    @Test
    void zeroBuyerOrganizationIdIsRejected() throws Exception {

        mockMvc.perform(
                get(BASE_URL + "/buyer/0")
                        .header(
                                "Authorization",
                                "Bearer " + buyerToken()
                        )
        ).andExpect(status().isBadRequest());
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

            return new SecretKeySpec(secretBytes, "HmacSHA256");
        }

        @Bean
        JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {

            return new NimbusJwtEncoder(
                    new ImmutableSecret<>(jwtSecretKey)
            );
        }
    }
}
