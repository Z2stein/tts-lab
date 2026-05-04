package com.example.ttslab.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MockLoginController {
    private static final Logger log = LoggerFactory.getLogger(MockLoginController.class);
    private final AuthProperties props;

    public MockLoginController(AuthProperties props) {
        this.props = props;
    }

    @PostMapping("/api/mock-login")
    public CurrentUser login(HttpServletRequest request) {
        CurrentUser mockUser = props.mockCurrentUser();
        MockUserPrincipal principal = MockUserPrincipal.from(mockUser);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            "N/A",
            principal.getAuthorities()
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        log.info("Mock login created session for user (id={}, email={})", mockUser.id(), mockUser.email());
        return mockUser;
    }
}
