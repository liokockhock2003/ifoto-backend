package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.util.List;


public record EquipmentSchedulesResponse(
        List<MainEquipmentScheduleEntry> mainEquipment,
        List<SubEquipmentScheduleEntry> subEquipment
) {}
