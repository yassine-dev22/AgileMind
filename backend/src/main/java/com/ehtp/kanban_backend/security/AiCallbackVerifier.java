package com.ehtp.kanban_backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AiCallbackVerifier {
    private final byte[] secret;

    public AiCallbackVerifier(@Value("${app.ai.callback-secret:}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length > 0 && this.secret.length < 32) {
            throw new IllegalArgumentException("AI_CALLBACK_SECRET must contain at least 32 private random bytes");
        }
    }

    public void verify(String suppliedToken) {
        if (secret.length == 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI callbacks are not configured");
        }
        if (suppliedToken == null || !MessageDigest.isEqual(secret, suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid AI callback credentials");
        }
    }
}
