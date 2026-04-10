package com.ifoto.ifoto_backend.validation;

import com.ifoto.ifoto_backend.dto.EventDTO.EventRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EventDateRangeValidator implements ConstraintValidator<EventDateRangeValid, EventRequest> {

    @Override
    public boolean isValid(EventRequest request, ConstraintValidatorContext context) {
        if (request.startDate() == null || request.endDate() == null) {
            return true; // null checks are handled by @NotNull on the fields
        }
        return !request.endDate().isBefore(request.startDate());
    }
}
