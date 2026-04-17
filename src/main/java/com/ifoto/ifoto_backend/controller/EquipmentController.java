package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.EquipmentListResponse;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.MainEquipmentRequest;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.MainEquipmentResponse;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.RentableEquipmentResponse;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentRequest;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentResponse;
import com.ifoto.ifoto_backend.model.MemberType;
import com.ifoto.ifoto_backend.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<EquipmentListResponse> getAllEquipment() {
        return ResponseEntity.ok(equipmentService.getAllEquipment());
    }

    @GetMapping("/rentable")
    public ResponseEntity<List<RentableEquipmentResponse>> getRentableEquipment(Authentication auth) {
        MemberType memberType = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_NON_STUDENT"))
                ? MemberType.NON_STUDENT : MemberType.STUDENT;
        return ResponseEntity.ok(equipmentService.getRentableEquipment(memberType));
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
}
