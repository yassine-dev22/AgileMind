package com.ehtp.kanban_backend;

import com.ehtp.kanban_backend.security.JwtService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTests {
    @Test
    void rejectsMissingWeakAndKnownDefaultSecrets() {
        for (String secret : new String[]{null, "", "short", "change-this-development-jwt-secret-at-least-32-chars"}) {
            assertThrows(IllegalArgumentException.class, () -> new JwtService(secret, 3600));
        }
    }

    @Test
    void rejectsNonPositiveLifetime() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("test-jwt-secret-that-is-long-enough-for-hmac", 0));
    }
}
