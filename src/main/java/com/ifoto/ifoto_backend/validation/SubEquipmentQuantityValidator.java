package com.ifoto.ifoto_backend.validation;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SubEquipmentQuantityValidator implements ConstraintValidator<SubEquipmentQuantityValid, SubEquipmentRequest> {

    @Override
    public boolean isValid(SubEquipmentRequest request, ConstraintValidatorContext context) {
        if (request.totalQuantity() < 0 || request.usedQuantity() < 0 || request.availableQuantity() < 0) {
            return true; // handled by @Min on individual fields
        }
        return request.usedQuantity() <= request.totalQuantity()
                && request.usedQuantity() + request.availableQuantity() == request.totalQuantity();
    }
}
