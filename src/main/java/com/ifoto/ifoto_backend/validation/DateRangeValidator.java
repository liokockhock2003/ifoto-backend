package com.ifoto.ifoto_backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<DateRangeValid, DateRangeValidatable> {

    @Override
    public boolean isValid(DateRangeValidatable value, ConstraintValidatorContext context) {
        if (value.getStartDate() == null || value.getEndDate() == null) {
            return true;
        }
        return !value.getEndDate().isBefore(value.getStartDate());
    }
}
