package com.ifoto.ifoto_backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = {SubEquipmentQuantityValidator.class})
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubEquipmentQuantityValid {
    String message() default "Quantity invariant violated: usedQuantity + availableQuantity must equal totalQuantity, and usedQuantity must not exceed totalQuantity";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
