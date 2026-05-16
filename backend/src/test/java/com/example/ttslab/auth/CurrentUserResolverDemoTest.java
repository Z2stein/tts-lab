package com.example.ttslab.auth;

import com.example.ttslab.prompts.CurrentUserResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserResolverDemoTest {
    private CurrentUserResolver resolver;

    @BeforeEach
    void setUp() {
        AuthProperties props = mock(AuthProperties.class);
        when(props.mode()).thenReturn(AuthMode.GOOGLE);
        resolver = new CurrentUserResolver(props);
    }

    @Test
    @DisplayName("GIVEN DemoAuthentication WHEN resolve THEN returns demo CurrentUser")
    void demoAuthenticationResolvesToDemoUser() {
        CurrentUser demoUser = new CurrentUser("demo-jti-123", "", "Hackathon-Besucher", List.of(), "demo");
        DemoAuthentication auth = new DemoAuthentication(demoUser);

        CurrentUser result = resolver.resolve(auth);

        assertThat(result.id()).isEqualTo("demo-jti-123");
        assertThat(result.name()).isEqualTo("Hackathon-Besucher");
        assertThat(result.authMode()).isEqualTo("demo");
    }

    @Test
    @DisplayName("GIVEN null authentication WHEN resolve THEN returns anonymous user")
    void nullAuthenticationResolvesToAnonymous() {
        CurrentUser result = resolver.resolve(null);

        assertThat(result.id()).isEqualTo("anonymous");
    }

    @Test
    @DisplayName("GIVEN mock mode WHEN resolve with DemoAuthentication THEN demo user wins over mock")
    void demoAuthenticationWinsOverMockMode() {
        AuthProperties mockProps = mock(AuthProperties.class);
        when(mockProps.mode()).thenReturn(AuthMode.MOCK);
        CurrentUserResolver mockResolver = new CurrentUserResolver(mockProps);

        CurrentUser demoUser = new CurrentUser("demo-jti", "", "Demo Visitor", List.of(), "demo");
        DemoAuthentication auth = new DemoAuthentication(demoUser);

        CurrentUser result = mockResolver.resolve(auth);

        assertThat(result.authMode()).isEqualTo("demo");
        assertThat(result.name()).isEqualTo("Demo Visitor");
    }

    @Test
    @DisplayName("GIVEN mock mode WHEN resolve without DemoAuthentication THEN returns mock user")
    void mockModeReturnsMockUserWithoutDemoAuth() {
        AuthProperties mockProps = mock(AuthProperties.class);
        when(mockProps.mode()).thenReturn(AuthMode.MOCK);
        CurrentUser mockUser = new CurrentUser("mock-1", "mock@example.com", "Mock User", List.of("USER"), "mock");
        when(mockProps.mockCurrentUser()).thenReturn(mockUser);
        CurrentUserResolver mockResolver = new CurrentUserResolver(mockProps);

        CurrentUser result = mockResolver.resolve(null);

        assertThat(result.authMode()).isEqualTo("mock");
    }
}
