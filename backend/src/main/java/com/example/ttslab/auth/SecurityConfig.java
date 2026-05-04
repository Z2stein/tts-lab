package com.example.ttslab.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProperties authProperties) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        if (authProperties.mode() == AuthMode.MOCK) {
            http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/actuator/health", "/api/mock-login").permitAll()
                .anyRequest().authenticated());
        } else {
            http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/actuator/health").permitAll()
                .anyRequest().authenticated());
            http.oauth2Login(oauth -> oauth.defaultSuccessUrl("/", true));
            http.logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"));
        }

        return http.build();
    }
}
