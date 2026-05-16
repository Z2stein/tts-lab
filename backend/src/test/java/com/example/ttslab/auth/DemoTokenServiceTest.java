package com.example.ttslab.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoTokenServiceTest {
    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac-sha256";
    private DemoTokenService service;

    @BeforeEach
    void setUp() {
        DemoTokenProperties props = new DemoTokenProperties(true, SECRET);
        service = new DemoTokenService(props);
    }

    @Test
    @DisplayName("GIVEN valid token WHEN validate THEN returns correct claims")
    void validTokenReturnsClaims() {
        String token = buildToken("demo-test-jti", "Test User", Instant.now().plusSeconds(3600));

        DemoTokenClaims claims = service.validate(token);

        assertThat(claims.jti()).isEqualTo("demo-test-jti");
        assertThat(claims.name()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("GIVEN expired token WHEN validate THEN throws InvalidDemoTokenException")
    void expiredTokenThrows() {
        String token = buildToken("demo-expired", "Expired User", Instant.now().minusSeconds(10));

        assertThatThrownBy(() -> service.validate(token))
            .isInstanceOf(InvalidDemoTokenException.class);
    }

    @Test
    @DisplayName("GIVEN token with wrong signature WHEN validate THEN throws InvalidDemoTokenException")
    void wrongSignatureThrows() {
        DemoTokenProperties otherProps = new DemoTokenProperties(true, "completely-different-secret-long-enough");
        DemoTokenService otherService = new DemoTokenService(otherProps);
        String token = buildToken("demo-other", "Other User", Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> otherService.validate(token))
            .isInstanceOf(InvalidDemoTokenException.class);
    }

    @Test
    @DisplayName("GIVEN disabled demo tokens WHEN validate THEN throws InvalidDemoTokenException")
    void disabledThrows() {
        DemoTokenService disabledService = new DemoTokenService(new DemoTokenProperties(false, SECRET));
        String token = buildToken("demo-test", "Test User", Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> disabledService.validate(token))
            .isInstanceOf(InvalidDemoTokenException.class);
    }

    private String buildToken(String jti, String name, Instant exp) {
        return Jwts.builder()
            .id(jti)
            .claim("name", name)
            .expiration(Date.from(exp))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}
