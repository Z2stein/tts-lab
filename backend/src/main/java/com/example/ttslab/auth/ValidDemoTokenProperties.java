package com.example.ttslab.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = DemoTokenPropertiesValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidDemoTokenProperties {
    String message() default "Invalid demo-token configuration";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
