package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.time.LocalDateTime;

public record EventConflictRow(
        Long equipmentId,
        Long eventRequestId,
        LocalDateTime effectiveEndDatetime,
        LocalDateTime effectiveStartDatetime) {}
