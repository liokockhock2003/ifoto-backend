package com.ifoto.ifoto_backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = {DateTimeRangeValidator.class})
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DateTimeRangeValid {
    String message() default "End datetime must not be before start datetime";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
