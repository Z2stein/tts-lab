package com.example.ttslab.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

public record MockUserPrincipal(String id, String email, String displayName, List<GrantedAuthority> authorities)
    implements Principal {

    static MockUserPrincipal from(CurrentUser user) {
        List<GrantedAuthority> mappedAuthorities = user.roles().stream()
            .map(String::trim)
            .filter(role -> !role.isEmpty())
            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
            .map(SimpleGrantedAuthority::new)
            .map(GrantedAuthority.class::cast)
            .toList();

        return new MockUserPrincipal(user.id(), user.email(), user.name(), mappedAuthorities);
    }

    @Override
    public String getName() {
        return email;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
