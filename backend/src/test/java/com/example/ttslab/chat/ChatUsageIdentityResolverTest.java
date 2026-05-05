package com.example.ttslab.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class ChatUsageIdentityResolverTest {

    @Test
    void authenticatedUserWins() {
        ChatUsageIdentityResolver resolver = new ChatUsageIdentityResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "header-user");
        DefaultOAuth2User principal = new DefaultOAuth2User(java.util.List.of(), Map.of("sub", "auth-user"), "sub");

        assertEquals("auth-user", resolver.resolve(new TestingAuthenticationToken(principal, null), request, "X-User-Id"));
    }

    @Test
    void configuredHeaderFallbackWorks() {
        ChatUsageIdentityResolver resolver = new ChatUsageIdentityResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Custom-Id", "header-user");

        assertEquals("header-user", resolver.resolve(null, request, "X-Custom-Id"));
    }

    @Test
    void ipFallbackWorks() {
        ChatUsageIdentityResolver resolver = new ChatUsageIdentityResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");

        assertEquals("ip:10.0.0.8", resolver.resolve(null, request, "X-User-Id"));
    }
}
