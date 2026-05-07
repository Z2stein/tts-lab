package com.example.ttslab.prompts;

import com.example.ttslab.auth.AuthMode;
import com.example.ttslab.auth.AuthProperties;
import com.example.ttslab.auth.CurrentUser;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {
    private final AuthProperties authProperties;

    public CurrentUserResolver(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public CurrentUser resolve(Authentication authentication) {
        if (authProperties.mode() == AuthMode.MOCK) {
            return authProperties.mockCurrentUser();
        }

        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            List<String> roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
            return new CurrentUser(
                user.getAttribute("sub"),
                user.getAttribute("email"),
                user.getAttribute("name"),
                roles,
                "google"
            );
        }

        return new CurrentUser("anonymous", null, "Anonymous", List.of(), authProperties.mode().name().toLowerCase());
    }
}
