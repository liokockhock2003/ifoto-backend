package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.validation.SubEquipmentQuantityValid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@SubEquipmentQuantityValid
public record SubEquipmentRequest(
        @NotBlank(message = "Equipment type is required")
        @Size(max = 100)
        String equipmentType,

        @Size(max = 100)
        String brand,

        @Size(max = 100)
        String model,

        @Min(value = 0, message = "Capacity cannot be negative")
        int capacity,

        @Min(value = 0, message = "Total quantity cannot be negative")
        int totalQuantity,

        @Min(value = 0, message = "Used quantity cannot be negative")
        int usedQuantity,

        @Min(value = 0, message = "Available quantity cannot be negative")
        int availableQuantity,

        String notes
) {}
