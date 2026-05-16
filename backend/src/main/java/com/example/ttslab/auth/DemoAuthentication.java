package com.example.ttslab.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import java.util.List;

public class DemoAuthentication extends AbstractAuthenticationToken {
    private final CurrentUser currentUser;

    public DemoAuthentication(CurrentUser currentUser) {
        super(List.of());
        this.currentUser = currentUser;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public CurrentUser getPrincipal() {
        return currentUser;
    }
}
