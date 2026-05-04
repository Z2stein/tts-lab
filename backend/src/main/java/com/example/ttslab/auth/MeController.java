package com.example.ttslab.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MeController {
    private static final Logger log = LoggerFactory.getLogger(MeController.class);
    private final AuthProperties props;

    public MeController(AuthProperties props) {
        this.props = props;
    }

    @GetMapping("/api/me")
    public CurrentUser me(Authentication authentication) {
        log.info("GET /api/me called (authMode={})", props.mode());

        if (props.mode() == AuthMode.MOCK) {
            CurrentUser mockUser = props.mockCurrentUser();
            log.info("Returning mock user from /api/me (id={}, email={})", mockUser.id(), mockUser.email());
            return mockUser;
        }

        DefaultOAuth2User user = (DefaultOAuth2User) authentication.getPrincipal();
        List<String> roles = user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        CurrentUser currentUser = new CurrentUser(
            user.getAttribute("sub"),
            user.getAttribute("email"),
            user.getAttribute("name"),
            roles,
            "google"
        );
        log.info("Returning Google user from /api/me (id={}, email={})", currentUser.id(), currentUser.email());
        return currentUser;
    }
}
