package com.example.ttslab.chat;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class ChatUsageIdentityResolver {

    public String resolve(Authentication authentication, HttpServletRequest request, String configuredHeader) {
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            String userId = user.getAttribute("sub");
            if (userId != null && !userId.isBlank()) {
                return userId;
            }
        }

        String headerValue = request.getHeader(configuredHeader);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }

        return "ip:" + request.getRemoteAddr();
    }
}
