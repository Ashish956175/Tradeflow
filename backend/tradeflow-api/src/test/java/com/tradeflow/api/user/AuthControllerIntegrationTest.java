package com.tradeflow.api.user;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.tradeflow.api.config.SecurityConfig;
import com.tradeflow.api.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        AuthControllerIntegrationTest.JwtTestConfiguration.class
})
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    // --------------------------------------------------
    // REGISTRATION TESTS
    // --------------------------------------------------

    @Test
    void register_withValidBuyerRequest_shouldReturnCreated() throws Exception {

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(sampleAuthResponse(UserRole.BUYER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Test Buyer",
                                    "email": "buyer@example.com",
                                    "password": "StrongPassword123!",
                                    "role": "BUYER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("test-jwt-token"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void register_withValidSupplierRequest_shouldReturnCreated() throws Exception {

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(sampleAuthResponse(UserRole.SUPPLIER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Test Supplier",
                                    "email": "supplier@example.com",
                                    "password": "StrongPassword123!",
                                    "role": "SUPPLIER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SUPPLIER"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void register_withAdminRole_shouldReturnForbidden() throws Exception {

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "Public registration for ADMIN is not allowed"
                ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Admin User",
                                    "email": "admin@example.com",
                                    "password": "StrongPassword123!",
                                    "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void register_withDuplicateEmail_shouldReturnConflict() throws Exception {

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "An account with this email already exists"
                ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Existing User",
                                    "email": "existing@example.com",
                                    "password": "StrongPassword123!",
                                    "role": "BUYER"
                                }
                                """))
                .andExpect(status().isConflict());

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void register_withMissingFields_shouldReturnBadRequest() throws Exception {

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "buyer@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    void register_withInvalidEmail_shouldReturnBadRequest() throws Exception {

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Test User",
                                    "email": "invalid-email",
                                    "password": "StrongPassword123!",
                                    "role": "BUYER"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    @Test
    void register_withWeakPassword_shouldReturnBadRequest() throws Exception {

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Test User",
                                    "email": "buyer@example.com",
                                    "password": "weak",
                                    "role": "BUYER"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    // --------------------------------------------------
    // LOGIN TESTS
    // --------------------------------------------------

    @Test
    void login_withValidCredentials_shouldReturnOk() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(sampleAuthResponse(UserRole.BUYER));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com",
                                    "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("test-jwt-token"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void login_withInvalidCredentials_shouldReturnUnauthorized() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com",
                                    "password": "WrongPassword123!"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void login_withInvalidEmail_shouldReturnBadRequest() throws Exception {

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "invalid-email",
                                    "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any(LoginRequest.class));
    }

    @Test
    void login_withMissingPassword_shouldReturnBadRequest() throws Exception {

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any(LoginRequest.class));
    }

    @Test
    void register_withoutAuthentication_shouldBePublic() throws Exception {

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(sampleAuthResponse(UserRole.BUYER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Test Buyer",
                                    "email": "buyer@example.com",
                                    "password": "StrongPassword123!",
                                    "role": "BUYER"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void login_withoutAuthentication_shouldBePublic() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(sampleAuthResponse(UserRole.BUYER));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "test@example.com",
                                    "password": "StrongPassword123!"
                                }
                                """))
                .andExpect(status().isOk());
    }

    // --------------------------------------------------
    // TEST DATA
    // --------------------------------------------------

    private AuthResponse sampleAuthResponse(UserRole role) {
        return new AuthResponse(
                1L,
                "Test User",
                "test@example.com",
                role,
                "Bearer",
                "test-jwt-token",
                java.time.LocalDateTime.of(2026, 1, 1, 10, 0)
        );
    }

    // --------------------------------------------------
    // JWT TEST CONFIGURATION
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
            byte[] secretBytes = Base64.getDecoder().decode(TEST_SECRET);

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