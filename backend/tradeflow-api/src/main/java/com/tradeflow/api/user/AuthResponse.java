package com.tradeflow.api.user;

import java.time.LocalDateTime;

public record AuthResponse(
        Long id,
        String fullName,
        String email,
        UserRole role,
        String tokenType,
        String accessToken,
        LocalDateTime createdAt
) {
}