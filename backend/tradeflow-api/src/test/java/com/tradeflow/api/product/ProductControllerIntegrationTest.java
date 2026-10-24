package com.tradeflow.api.product;

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

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({
        SecurityConfig.class,
        ProductControllerIntegrationTest.JwtTestConfiguration.class
})
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private ProductService productService;

    private String createToken(String role) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("tradeflow-api")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1800))
                .subject("buyer@test.com")
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

    @Test
    void getProducts_withoutAuthentication_shouldReturnUnauthorized()
            throws Exception {

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProductById_withoutAuthentication_shouldReturnUnauthorized()
            throws Exception {

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createProduct_withoutAuthentication_shouldReturnUnauthorized()
            throws Exception {

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Safety Gloves",
                                    "description": "Industrial safety gloves",
                                    "sku": "TG-001",
                                    "unitPrice": 150.00,
                                    "availableQuantity": 100,
                                    "unit": "PAIR"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProducts_withValidToken_shouldReturnOk()
            throws Exception {

        when(productService.getAllProducts())
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/products")
                        .header(
                                "Authorization",
                                "Bearer " + createToken("BUYER")
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getProductById_withValidToken_shouldReturnProduct()
            throws Exception {

        Product product = new Product();
        product.setName("Safety Gloves");
        product.setDescription("Industrial safety gloves");
        product.setSku("TG-001");

        when(productService.getProductById(1L))
                .thenReturn(product);

        mockMvc.perform(get("/api/v1/products/1")
                        .header(
                                "Authorization",
                                "Bearer " + createToken("BUYER")
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Safety Gloves"))
                .andExpect(jsonPath("$.description")
                        .value("Industrial safety gloves"))
                .andExpect(jsonPath("$.sku").value("TG-001"));
    }


    @Test
    void createProduct_withValidToken_shouldReturnCreated()
            throws Exception {

        Product product = new Product();
        product.setName("Safety Gloves");
        product.setDescription("Industrial safety gloves");
        product.setSku("TG-001");

        when(productService.createProduct(any(ProductRequest.class)))
                .thenReturn(product);

        mockMvc.perform(post("/api/v1/products")
                        .header(
                                "Authorization",
                                "Bearer " + createToken("BUYER")
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Safety Gloves",
                                    "description": "Industrial safety gloves",
                                    "sku": "TG-001",
                                    "unitPrice": 150.00,
                                    "availableQuantity": 100,
                                    "unit": "PAIR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Safety Gloves"))
                .andExpect(jsonPath("$.sku").value("TG-001"));
    }

    @Test
    void createProduct_withInvalidData_shouldReturnBadRequest()
            throws Exception {

        mockMvc.perform(post("/api/v1/products")
                        .header(
                                "Authorization",
                                "Bearer " + createToken("BUYER")
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "",
                                    "description": "Invalid product",
                                    "sku": "",
                                    "unitPrice": -10,
                                    "availableQuantity": -1,
                                    "unit": "PAIR"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class JwtTestConfiguration {

        private static final String TEST_SECRET =
                Base64.getEncoder().encodeToString(
                        "tradeflow-integration-test-secret-key-32"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
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