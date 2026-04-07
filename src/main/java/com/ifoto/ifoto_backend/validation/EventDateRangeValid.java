package com.ifoto.ifoto_backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = {EventDateRangeValidator.class})
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventDateRangeValid {
    String message() default "End date must not be before start date";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
