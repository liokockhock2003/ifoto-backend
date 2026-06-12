package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.model.enumerator.MainEquipmentStatusType;

public record StatusTypeRow(Long equipmentId, MainEquipmentStatusType statusType) {}
