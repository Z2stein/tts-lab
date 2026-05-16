package com.example.ttslab.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "AUTH_MODE=mock",
    "ENVIRONMENT=feature",
    "MOCK_USER_ID=mock-1",
    "MOCK_USER_EMAIL=mock@example.com",
    "MOCK_USER_NAME=Mock User",
    "MOCK_USER_ROLES=USER",
    "DEMO_TOKEN_ENABLED=true",
    "TTS_LAB_DEMO_TOKEN_SIGNING_SECRET=test-secret-that-is-long-enough-for-hmac-sha256",
    "spring.ai.model.chat=google-genai",
    "spring.autoconfigure.exclude=org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration"
})
@AutoConfigureMockMvc
class DemoActivationControllerTest {
    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac-sha256";

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("GIVEN valid token WHEN activate THEN session contains SecurityContext and redirects to /")
    void validTokenSetsSessionAndRedirects() throws Exception {
        String token = buildToken("demo-test-jti", "Hackathon-Besucher", Instant.now().plusSeconds(3600));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/api/demo/activate").param("token", token).session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));

        Object securityContext = session.getAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(securityContext).isNotNull();
    }

    @Test
    @DisplayName("GIVEN expired token WHEN activate THEN redirects to /?demo-error=true")
    void expiredTokenRedirectsWithError() throws Exception {
        String token = buildToken("demo-expired", "Expired User", Instant.now().minusSeconds(10));

        mockMvc.perform(get("/api/demo/activate").param("token", token))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/?demo-error=true"));
    }

    @Test
    @DisplayName("GIVEN garbage token WHEN activate THEN redirects to /?demo-error=true")
    void invalidTokenRedirectsWithError() throws Exception {
        mockMvc.perform(get("/api/demo/activate").param("token", "not-a-valid-jwt"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/?demo-error=true"));
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
