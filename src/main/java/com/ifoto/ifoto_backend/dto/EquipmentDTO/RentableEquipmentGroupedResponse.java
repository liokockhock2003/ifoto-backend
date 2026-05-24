package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.util.List;

public record RentableEquipmentGroupedResponse(
        List<RentableEquipmentResponse> mainEquipment,
        List<RentableSubEquipmentResponse> subEquipment
) {}
