package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.time.LocalDateTime;

public record StatusConflictRow(
        Long equipmentId,
        Long statusId,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime) {}
