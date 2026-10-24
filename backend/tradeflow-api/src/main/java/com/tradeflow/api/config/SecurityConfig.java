package com.tradeflow.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken>
                    jwtAuthenticationConverter
    ) throws Exception {

        http
                // Disable CSRF for the stateless REST API
                .csrf(csrf -> csrf.disable())

                // Use stateless authentication
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Configure authorization rules
                .authorizeHttpRequests(auth -> auth

                        // Public health endpoint
                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health"
                        ).permitAll()

                        // Public authentication endpoints
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login"
                        ).permitAll()

                        // Organization onboarding
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/organizations/onboard"
                        ).hasAnyRole("BUYER", "SUPPLIER")

                        // Legacy organization creation: admin only
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/organizations"
                        ).hasRole("ADMIN")

                        // Organization reads
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/organizations",
                                "/api/v1/organizations/**"
                        ).authenticated()

                        // Create a buyer-supplier relationship
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/relationships"
                        ).hasRole("BUYER")

                        // View buyer-supplier relationships
                        // Ownership is checked in RelationshipService
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/relationships/**"
                        ).authenticated()

                        // Approve or reject a relationship
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/relationships/*/approve",
                                "/api/v1/relationships/*/reject"
                        ).hasAnyRole("SUPPLIER", "ADMIN")

                        // Procurement request creation
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/procurement-requests"
                        ).hasRole("BUYER")

                        // Procurement request reads
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/procurement-requests",
                                "/api/v1/procurement-requests/**"
                        ).hasAnyRole("BUYER", "ADMIN")

                        // Submit a quotation
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/quotations"
                        ).hasRole("SUPPLIER")

                        // Accept or reject a quotation
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/quotations/*/accept",
                                "/api/v1/quotations/*/reject"
                        ).hasRole("BUYER")

                        // Create an order from an accepted quotation
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/orders/quotation/*"
                        ).hasRole("BUYER")

                        // Create a payment
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/payments"
                        ).hasRole("BUYER")

                        // Create a shipment
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/shipments"
                        ).hasRole("SUPPLIER")

                        // Product catalogue: buyers, suppliers, and admins can read
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/products",
                                "/api/v1/products/**"
                        ).hasAnyRole("BUYER", "SUPPLIER", "ADMIN")

                        // Only suppliers and admins can create products
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/products"
                        ).hasAnyRole("SUPPLIER", "ADMIN")

                        // All remaining endpoints require authentication.
                        // IMPORTANT: Keep this rule last.
                        .anyRequest().authenticated()
                )

                // Configure JWT authentication
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                );

        return http.build();
    }
}
