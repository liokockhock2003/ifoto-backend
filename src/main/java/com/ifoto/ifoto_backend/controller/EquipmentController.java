package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.*;
import com.ifoto.ifoto_backend.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<EquipmentListResponse> getAllEquipment(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long excludeRentalId) {
        long exclude = excludeRentalId != null ? excludeRentalId : 0L;
        return ResponseEntity.ok(startDate != null && endDate != null
                ? equipmentService.getAllEquipment(startDate, endDate, exclude)
                : equipmentService.getAllEquipment());
    }

    @GetMapping("/available")
    public ResponseEntity<EquipmentListResponse> getAvailableEquipment(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String context,
            @RequestParam(required = false) Long excludeRentalId) {
        long exclude = excludeRentalId != null ? excludeRentalId : 0L;
        return ResponseEntity.ok(equipmentService.getAvailableEquipment(startDate, endDate, context, exclude));
    }

    // ── Main Equipment ────────────────────────────────────────────────────────

    @PostMapping("/main")
    public ResponseEntity<MainEquipmentResponse> addMainEquipment(
            @Valid @RequestBody MainEquipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(equipmentService.addMainEquipment(request));
    }

    @PutMapping("/main/{id}")
    public ResponseEntity<MainEquipmentResponse> updateMainEquipment(
            @PathVariable Long id,
            @Valid @RequestBody MainEquipmentRequest request) {
        return ResponseEntity.ok(equipmentService.updateMainEquipment(id, request));
    }

    @DeleteMapping("/main/{id}")
    public ResponseEntity<Void> deleteMainEquipment(@PathVariable Long id) {
        equipmentService.deleteMainEquipment(id);
        return ResponseEntity.noContent().build();
    }

    // ── Main Equipment Status ─────────────────────────────────────────────────

    @PostMapping("/main/{id}/statuses")
    public ResponseEntity<MainEquipmentStatusResponse> addMainEquipmentStatus(
            @PathVariable Long id,
            @Valid @RequestBody MainEquipmentStatusRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(equipmentService.addMainEquipmentStatus(id, request));
    }

    @GetMapping("/main/{id}/statuses")
    public ResponseEntity<List<MainEquipmentStatusResponse>> getMainEquipmentStatuses(
            @PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getMainEquipmentStatuses(id));
    }

    @PutMapping("/main/{id}/statuses/{statusId}")
    public ResponseEntity<MainEquipmentStatusResponse> updateMainEquipmentStatus(
            @PathVariable Long id,
            @PathVariable Long statusId,
            @Valid @RequestBody MainEquipmentStatusRequest request) {
        return ResponseEntity.ok(equipmentService.updateMainEquipmentStatus(id, statusId, request));
    }

    @DeleteMapping("/main/{id}/statuses/{statusId}")
    public ResponseEntity<Void> deleteMainEquipmentStatus(
            @PathVariable Long id,
            @PathVariable Long statusId) {
        equipmentService.deleteMainEquipmentStatus(id, statusId);
        return ResponseEntity.noContent().build();
    }

    // ── Sub Equipment ─────────────────────────────────────────────────────────

    @PostMapping("/sub")
    public ResponseEntity<SubEquipmentResponse> addSubEquipment(
            @Valid @RequestBody SubEquipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(equipmentService.addSubEquipment(request));
    }

    @PutMapping("/sub/{id}")
    public ResponseEntity<SubEquipmentResponse> updateSubEquipment(
            @PathVariable Long id,
            @Valid @RequestBody SubEquipmentRequest request) {
        return ResponseEntity.ok(equipmentService.updateSubEquipment(id, request));
    }

    @DeleteMapping("/sub/{id}")
    public ResponseEntity<Void> deleteSubEquipment(@PathVariable Long id) {
        equipmentService.deleteSubEquipment(id);
        return ResponseEntity.noContent().build();
    }

    // ── Sub Equipment Quantity Holds ──────────────────────────────────────────

    @PostMapping("/sub/{id}/quantity-holds")
    public ResponseEntity<SubEquipmentQuantityHoldResponse> addQuantityHold(
            @PathVariable Long id,
            @Valid @RequestBody SubEquipmentQuantityHoldRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(equipmentService.addQuantityHold(id, request));
    }

    @GetMapping("/sub/{id}/quantity-holds")
    public ResponseEntity<List<SubEquipmentQuantityHoldResponse>> getQuantityHolds(
            @PathVariable Long id) {
        return ResponseEntity.ok(equipmentService.getQuantityHolds(id));
    }

    @PutMapping("/sub/{id}/quantity-holds/{holdId}")
    public ResponseEntity<SubEquipmentQuantityHoldResponse> updateQuantityHold(
            @PathVariable Long id,
            @PathVariable Long holdId,
            @Valid @RequestBody SubEquipmentQuantityHoldRequest request) {
        return ResponseEntity.ok(equipmentService.updateQuantityHold(id, holdId, request));
    }

    @DeleteMapping("/sub/{id}/quantity-holds/{holdId}")
    public ResponseEntity<Void> deleteQuantityHold(
            @PathVariable Long id,
            @PathVariable Long holdId) {
        equipmentService.deleteQuantityHold(id, holdId);
        return ResponseEntity.noContent().build();
    }
}
