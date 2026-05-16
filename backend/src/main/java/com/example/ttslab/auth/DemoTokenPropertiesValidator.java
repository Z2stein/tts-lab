package com.example.ttslab.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DemoTokenPropertiesValidator implements ConstraintValidator<ValidDemoTokenProperties, DemoTokenProperties> {
    @Override
    public boolean isValid(DemoTokenProperties value, ConstraintValidatorContext context) {
        if (value == null || !value.enabled()) {
            return true;
        }

        if (value.signingSecret() == null || value.signingSecret().isBlank()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "TTS_LAB_DEMO_TOKEN_SIGNING_SECRET must not be blank when demo-token.enabled=true")
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
