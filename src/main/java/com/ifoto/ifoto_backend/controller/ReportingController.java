package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.ReportDTO.ReportingDTO.*;
import com.ifoto.ifoto_backend.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;

    @GetMapping("/kpi")
    public ResponseEntity<KpiResponse> kpi() {
        return ResponseEntity.ok(reportingService.kpi());
    }

    @GetMapping("/rental-status")
    public ResponseEntity<RentalStatusSummary> rentalStatus() {
        return ResponseEntity.ok(reportingService.rentalStatusBreakdown());
    }

    @GetMapping("/rental-volume")
    public ResponseEntity<List<RentalVolumeItem>> rentalVolume(
            @RequestParam(defaultValue = "12") int months) {
        return ResponseEntity.ok(reportingService.rentalVolume(months));
    }

    @GetMapping("/revenue")
    public ResponseEntity<List<RevenueItem>> revenue(
            @RequestParam(defaultValue = "12") int months) {
        return ResponseEntity.ok(reportingService.revenue(months));
    }

    @GetMapping("/equipment-utilization")
    public ResponseEntity<List<EquipmentUtilizationItem>> equipmentUtilization() {
        return ResponseEntity.ok(reportingService.equipmentUtilization());
    }
}
