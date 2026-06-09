package com.ifoto.ifoto_backend.dto.ReportDTO;

public interface EquipmentUtilizationProjection {
    Long getEquipmentId();
    String getBrand();
    String getModel();
    String getEquipmentType();
    long getRentalCount();
}
