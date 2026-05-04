package com.example.ttslab.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class AuthConfigValidator {
    private final AuthProperties props;

    public AuthConfigValidator(AuthProperties props) {
        this.props = props;
    }

    @PostConstruct
    void validate() {
        if (props.mode() == AuthMode.MOCK && ("main".equals(props.environment()) || "prod".equals(props.environment()))) {
            throw new IllegalStateException("AUTH_MODE=mock is forbidden for main/prod environments");
        }
        if (props.mode() == AuthMode.GOOGLE && (isMissing(props.googleClientId()) || isMissing(props.googleClientSecret()))) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are required in google mode");
        }
    }

    private boolean isMissing(String v) {
        return v == null || v.isBlank() || "unset".equals(v);
    }
}
