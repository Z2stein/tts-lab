package com.example.ttslab.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class DemoActivationController {
    private static final Logger log = LoggerFactory.getLogger(DemoActivationController.class);

    private final DemoTokenService demoTokenService;

    public DemoActivationController(DemoTokenService demoTokenService) {
        this.demoTokenService = demoTokenService;
    }

    @GetMapping("/api/demo/activate")
    public RedirectView activate(@RequestParam String token, HttpServletRequest request) {
        try {
            DemoTokenClaims claims = demoTokenService.validate(token);
            CurrentUser demoUser = new CurrentUser(claims.jti(), "", claims.name(), List.of(), "demo");

            DemoAuthentication auth = new DemoAuthentication(demoUser);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            log.info("Demo token activated for user '{}' (jti={})", claims.name(), claims.jti());
            return new RedirectView("/");
        } catch (InvalidDemoTokenException e) {
            log.warn("Demo token activation failed: {}", e.getMessage());
            return new RedirectView("/?demo-error=true");
        }
    }
}
