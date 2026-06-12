package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.time.LocalDateTime;

public record SubBoundaryRow(Long subEquipmentId, Long id, LocalDateTime datetime, Long quantity) {}
