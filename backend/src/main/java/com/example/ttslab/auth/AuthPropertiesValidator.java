package com.example.ttslab.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AuthPropertiesValidator implements ConstraintValidator<ValidAuthProperties, AuthProperties> {
    @Override
    public boolean isValid(AuthProperties value, ConstraintValidatorContext context) {
        if (value == null || value.mode() == null) {
            return true;
        }

        if (value.mode() == AuthMode.MOCK) {
            if (isMainOrProd(value.environment())) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("AUTH_MODE=mock is forbidden for main/prod environments")
                    .addConstraintViolation();
                return false;
            }
            return true;
        }

        if (value.mode() == AuthMode.GOOGLE && (isMissing(value.googleClientId()) || isMissing(value.googleClientSecret()))) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are required in google mode")
                .addConstraintViolation();
            return false;
        }

        return true;
    }

    private boolean isMainOrProd(String environment) {
        if (environment == null) {
            return false;
        }
        return "main".equalsIgnoreCase(environment) || "prod".equalsIgnoreCase(environment);
    }

    private boolean isMissing(String value) {
        return value == null || value.isBlank() || "unset".equalsIgnoreCase(value.trim());
    }
}
