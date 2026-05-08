package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.time.LocalDate;

public record BookedDateRange(Long equipmentId, LocalDate startDate, LocalDate endDate, boolean pending) {}
