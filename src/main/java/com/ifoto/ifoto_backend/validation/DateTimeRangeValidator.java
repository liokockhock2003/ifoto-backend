package com.ifoto.ifoto_backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateTimeRangeValidator implements ConstraintValidator<DateTimeRangeValid, DateTimeRangeValidatable> {

    @Override
    public boolean isValid(DateTimeRangeValidatable value, ConstraintValidatorContext context) {
        if (value.getStartDatetime() == null || value.getEndDatetime() == null) {
            return true;
        }
        return !value.getEndDatetime().isBefore(value.getStartDatetime());
    }
}
