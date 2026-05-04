package com.example.ttslab.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MeController {
    private final AuthProperties props;

    public MeController(AuthProperties props) {
        this.props = props;
    }

    @GetMapping("/api/me")
    public CurrentUser me(Authentication authentication) {
        if (props.mode() == AuthMode.MOCK) {
            return props.mockCurrentUser();
        }

        DefaultOAuth2User user = (DefaultOAuth2User) authentication.getPrincipal();
        List<String> roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return new CurrentUser(
            user.getAttribute("sub"),
            user.getAttribute("email"),
            user.getAttribute("name"),
            roles,
            "google"
        );
    }
}
