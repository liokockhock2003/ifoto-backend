package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.util.List;

public record EquipmentListResponse(
        List<MainEquipmentResponse> mainEquipment,
        List<SubEquipmentResponse> subEquipment
) {}
