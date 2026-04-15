package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MainEquipmentRequest(
        @NotBlank(message = "Equipment type is required")
        @Size(max = 100)
        String equipmentType,

        @Size(max = 50)
        String lensType,

        @Size(max = 100)
        String brand,

        @Size(max = 100)
        String model,

        @Size(max = 100)
        String serialNumber,

        @Size(max = 50)
        String condition,

        @Size(max = 50)
        String status,

        String notes
) {}
