package com.example.ttslab.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class DemoTokenService {
    private final DemoTokenProperties properties;

    public DemoTokenService(DemoTokenProperties properties) {
        this.properties = properties;
    }

    public DemoTokenClaims validate(String rawJwt) {
        if (!properties.enabled()) {
            throw new InvalidDemoTokenException("Demo tokens are disabled");
        }

        try {
            SecretKey key = Keys.hmacShaKeyFor(properties.signingSecret().getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(rawJwt)
                .getPayload();

            String jti = claims.getId();
            String name = claims.get("name", String.class);

            if (jti == null || jti.isBlank()) {
                throw new InvalidDemoTokenException("Token is missing jti claim");
            }
            if (name == null || name.isBlank()) {
                throw new InvalidDemoTokenException("Token is missing name claim");
            }

            return new DemoTokenClaims(jti, name);
        } catch (JwtException e) {
            throw new InvalidDemoTokenException("Invalid or expired demo token: " + e.getMessage(), e);
        }
    }
}
