package com.tradeflow.api.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder(12);

        String password = "TradeFlowAdmin@2026";

        System.out.println(
                "Password hash: " + encoder.encode(password)
        );
    }
}