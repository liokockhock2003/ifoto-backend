package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RentalConflictRow(
        Long equipmentId,
        Long rentalId,
        LocalDateTime returnDatetime,
        LocalDateTime pickupDatetime,
        LocalDate programStartDate,
        LocalDate programEndDate) {}
