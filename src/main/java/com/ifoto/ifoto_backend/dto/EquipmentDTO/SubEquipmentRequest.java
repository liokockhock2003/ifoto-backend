package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubEquipmentRequest(
        @Size(max = 100)
        String type,

        @NotBlank(message = "Equipment type is required")
        @Size(max = 100)
        String equipmentType,

        List<String> cameraModel,

        @Size(max = 100)
        String brand,

        @Min(value = 0, message = "Capacity cannot be negative")
        int capacity,

        @Min(value = 0, message = "Total quantity cannot be negative")
        int totalQuantity,

        String notes,

        Long pricingCategoryId,

        Boolean isForRent
) {}
