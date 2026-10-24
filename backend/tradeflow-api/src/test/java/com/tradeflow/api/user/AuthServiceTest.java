package com.tradeflow.api.user;

import com.tradeflow.api.config.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest(
                "Ashish Gaikwad",
                "ASHISH@example.com",
                "StrongPassword@123",
                UserRole.BUYER
        );

        loginRequest = new LoginRequest(
                "ASHISH@example.com",
                "StrongPassword@123"
        );
    }

    @Test
    void registerShouldCreateBuyerAccount() {
        when(userRepository.existsByEmail("ashish@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("StrongPassword@123"))
                .thenReturn("hashed-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    return user;
                });

        when(jwtService.generateToken(any(User.class)))
                .thenReturn("test-jwt-token");

        AuthResponse response = authService.register(registerRequest);

        assertEquals("Ashish Gaikwad", response.fullName());
        assertEquals("ashish@example.com", response.email());
        assertEquals(UserRole.BUYER, response.role());
        assertEquals("Bearer", response.tokenType());
        assertEquals("test-jwt-token", response.accessToken());

        verify(userRepository).existsByEmail("ashish@example.com");
        verify(passwordEncoder).encode("StrongPassword@123");
        verify(userRepository).save(any(User.class));
        verify(jwtService).generateToken(any(User.class));
    }

    @Test
    void registerShouldRejectAdminRole() {
        RegisterRequest adminRequest = new RegisterRequest(
                "Administrator",
                "admin@example.com",
                "StrongPassword@123",
                UserRole.ADMIN
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(adminRequest)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(userRepository, never()).save(any(User.class));
        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void registerShouldRejectExistingEmail() {
        when(userRepository.existsByEmail("ashish@example.com"))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(registerRequest)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginShouldAuthenticateValidCredentials() {
        User user = new User(
                "Ashish Gaikwad",
                "ashish@example.com",
                "hashed-password",
                UserRole.BUYER
        );

        when(userRepository.findByEmail("ashish@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "StrongPassword@123",
                "hashed-password"
        )).thenReturn(true);

        when(jwtService.generateToken(user))
                .thenReturn("test-jwt-token");

        AuthResponse response = authService.login(loginRequest);

        assertEquals("ashish@example.com", response.email());
        assertEquals(UserRole.BUYER, response.role());
        assertEquals("test-jwt-token", response.accessToken());

        verify(passwordEncoder).matches(
                "StrongPassword@123",
                "hashed-password"
        );

        verify(jwtService).generateToken(user);
    }

    @Test
    void loginShouldRejectIncorrectPassword() {
        User user = new User(
                "Ashish Gaikwad",
                "ashish@example.com",
                "hashed-password",
                UserRole.BUYER
        );

        when(userRepository.findByEmail("ashish@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "WrongPassword@123",
                "hashed-password"
        )).thenReturn(false);

        LoginRequest incorrectPasswordRequest = new LoginRequest(
                "ashish@example.com",
                "WrongPassword@123"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(incorrectPasswordRequest)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void loginShouldRejectUnknownEmail() {
        when(userRepository.findByEmail("ashish@example.com"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());

        verify(passwordEncoder, never()).matches(
                anyString(),
                anyString()
        );

        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void loginShouldRejectDisabledAccount() {
        User user = new User(
                "Ashish Gaikwad",
                "ashish@example.com",
                "hashed-password",
                UserRole.BUYER
        );

        user.setEnabled(false);

        when(userRepository.findByEmail("ashish@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "StrongPassword@123",
                "hashed-password"
        )).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verify(jwtService, never()).generateToken(any(User.class));
    }
}