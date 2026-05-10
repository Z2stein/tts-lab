package com.example.ttslab.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.ttslab.auth.AuthMode;
import com.example.ttslab.auth.AuthProperties;
import com.example.ttslab.auth.CurrentUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

@DisplayName("CurrentUserResolver")
class CurrentUserResolverTest {

    private AuthProperties createAuthPropertiesMock(AuthMode mode) {
        AuthProperties props = mock(AuthProperties.class);
        when(props.mode()).thenReturn(mode);
        return props;
    }

    private AuthProperties createAuthPropertiesWithMockUser(AuthMode mode, CurrentUser mockUser) {
        AuthProperties props = mock(AuthProperties.class);
        when(props.mode()).thenReturn(mode);
        when(props.mockCurrentUser()).thenReturn(mockUser);
        return props;
    }

    // ============ MOCK MODE TESTS ============

    @Test
    @DisplayName("resolve returns mock user when auth mode is MOCK")
    void resolveMockMode() {
        CurrentUser mockUser = new CurrentUser("mock-id", "mock@example.com", "Mock User", List.of("MOCK"), "mock");
        AuthProperties authProperties = createAuthPropertiesWithMockUser(AuthMode.MOCK, mockUser);
        CurrentUserResolver resolver = new CurrentUserResolver(authProperties);

        CurrentUser result = resolver.resolve(null);

        assertThat(result)
            .isNotNull()
            .extracting(CurrentUser::id, CurrentUser::email, CurrentUser::name, CurrentUser::authMode)
            .containsExactly("mock-id", "mock@example.com", "Mock User", "mock");
    }

    @Test
    @DisplayName("resolve ignores authentication when auth mode is MOCK")
    void resolveMockModeIgnoresAuthentication() {
        CurrentUser mockUser = new CurrentUser("mock-id", "mock@example.com", "Mock User", List.of("MOCK"), "mock");
        AuthProperties authProperties = createAuthPropertiesWithMockUser(AuthMode.MOCK, mockUser);
        CurrentUserResolver resolver = new CurrentUserResolver(authProperties);

        Authentication auth = mock(Authentication.class);
        CurrentUser result = resolver.resolve(auth);

        assertThat(result.id()).isEqualTo("mock-id");
    }

    // ============ OAUTH2 MODE TESTS ============

    @Test
    @DisplayName("resolve returns OAuth2 user with valid attributes")
    void resolveOAuth2WithValidAttributes() {
        AuthProperties authProperties = createAuthPropertiesMock(AuthMode.GOOGLE);
        CurrentUserResolver resolver = new CurrentUserResolver(authProperties);

        OAuth2User oauth2User = createOAuth2User("sub123", "user@example.com", "John Doe", List.of("ROLE_USER"));
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);

        CurrentUser result = resolver.resolve(auth);

        assertThat(result)
            .extracting(CurrentUser::id, CurrentUser::email, CurrentUser::name, CurrentUser::authMode)
            .containsExactly("sub123", "user@example.com", "John Doe", "google");
        assertThat(result.roles()).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("resolve maps OAuth2 authorities to roles")
    void resolveOAuth2MapsAuthorities() {
        AuthProperties authProperties = createAuthPropertiesMock(AuthMode.GOOGLE);
        CurrentUserResolver resolver = new CurrentUserResolver(authProperties);

        OAuth2User oauth2User = createOAuth2User("sub123", "user@example.com", "John Doe",
            List.of("ROLE_USER", "ROLE_ADMIN"));
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);

        CurrentUser result = resolver.resolve(auth);

        assertThat(result.roles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("resolve handles OAuth2 user with empty authorities")
    void resolveOAuth2EmptyAuthorities() {
        AuthProperties authProperties = createAuthPropertiesMock(AuthMode.GOOGLE);
        CurrentUserResolver resolver = new CurrentUserResolver(authProperties);

        OAuth2User oauth2User = createOAuth2User("sub123", "user@example.com", "John Doe", List.of());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);

        CurrentUser result = resolver.resolve(auth);

        assertThat(result.roles()).isEmpty();
    }

    @Test
    @DisplayName("resolve handles multiple authorities from OAuth2")
    void resolveOAuth2MultipleAuthorities() {
        AuthProperties authProperties = createAuthPropertiesMock(AuthMode.GOOGLE);
        CurrentUserResolver resolver = new CurrentUserResolver(authProperties);

        OAuth2User oauth2User = createOAuth2User("sub456", "admin@example.com", "Admin User",
            List.of("ROLE_ADMIN", "ROLE_USER", "ROLE_MODERATOR"));
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);

        CurrentUser result = resolver.resolve(auth);

        assertThat(result.roles()).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER", "ROLE_MODERATOR");
    }

    // ============ NULL/EDGE CASE TESTS ============

    @Test
    @DisplayName("resolve returns anonymous user when authentication is null")
    void resolveNullAuthentication() {
        AuthProperties authProperties = createAuthPropertiesMock(AuthMode.GOOGLE);
        CurrentUserResolver resolver = new CurrentUserResolver(authProperties);

        CurrentUser result = resolver.resolve(null);

        assertThat(result.id()).isEqualTo("anonymous");
        assertThat(result.email()).isNull();
        assertThat(result.name()).isEqualTo("Anonymous");
        assertThat(result.roles()).isEmpty();
    }

    @Test
    @DisplayName("resolve returns anonymous user when principal is not OAuth2User")
    void resolveNonOAuth2Principal() {
        AuthProperties authProperties = createAuthPropertiesMock(AuthMode.GOOGLE);
        CurrentUserResolver resolver = new CurrentUserResolver(authProperties);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("string-principal");

        CurrentUser result = resolver.resolve(auth);

        assertThat(result.id()).isEqualTo("anonymous");
        assertThat(result.name()).isEqualTo("Anonymous");
    }

    @Test
    @DisplayName("resolve returns anonymous user when principal is null")
    void resolveNullPrincipal() {
        AuthProperties authProperties = createAuthPropertiesMock(AuthMode.GOOGLE);
        CurrentUserResolver resolver = new CurrentUserResolver(authProperties);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(null);

        CurrentUser result = resolver.resolve(auth);

        assertThat(result.id()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("resolve uses GOOGLE authMode for OAuth2 users")
    void resolveOAuth2AuthMode() {
        AuthProperties authProperties = createAuthPropertiesMock(AuthMode.GOOGLE);
        CurrentUserResolver resolver = new CurrentUserResolver(authProperties);

        OAuth2User oauth2User = createOAuth2User("sub", "user@example.com", "User", List.of());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);

        CurrentUser result = resolver.resolve(auth);

        assertThat(result.authMode()).isEqualTo("google");
    }

    @Test
    @DisplayName("resolve uses lowercase authMode name for anonymous users")
    void resolveAnonymousAuthMode() {
        AuthProperties authProperties = createAuthPropertiesMock(AuthMode.GOOGLE);
        CurrentUserResolver resolver = new CurrentUserResolver(authProperties);

        CurrentUser result = resolver.resolve(null);

        assertThat(result.authMode()).isEqualTo("google");
    }

    // ============ HELPER METHODS ============

    private OAuth2User createOAuth2User(String sub, String email, String name, List<String> authorities) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", sub);
        attributes.put("email", email);
        attributes.put("name", name);

        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        for (String auth : authorities) {
            grantedAuthorities.add(new SimpleGrantedAuthority(auth));
        }

        return new DefaultOAuth2User(grantedAuthorities, attributes, "sub");
    }
}
