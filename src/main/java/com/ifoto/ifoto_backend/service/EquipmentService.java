package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.EquipmentListResponse;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.MainEquipmentRequest;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.MainEquipmentResponse;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentRequest;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentResponse;
import com.ifoto.ifoto_backend.model.MainEquipment;
import com.ifoto.ifoto_backend.model.SubEquipment;
import com.ifoto.ifoto_backend.repository.MainEquipmentRepository;
import com.ifoto.ifoto_backend.repository.SubEquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final MainEquipmentRepository mainEquipmentRepository;
    private final SubEquipmentRepository subEquipmentRepository;

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EquipmentListResponse getAllEquipment() {
        return buildEquipmentListResponse();
    }

    // ── Main Equipment ────────────────────────────────────────────────────────

    @Transactional
    public EquipmentListResponse addMainEquipment(MainEquipmentRequest req) {
        MainEquipment entity = MainEquipment.builder()
                .equipmentType(req.equipmentType())
                .brand(req.brand())
                .model(req.model())
                .serialNumber(req.serialNumber())
                .condition(req.condition())
                .status(req.status())
                .notes(req.notes())
                .build();
        mainEquipmentRepository.save(entity);
        return buildEquipmentListResponse();
    }

    @Transactional
    public EquipmentListResponse updateMainEquipment(Long id, MainEquipmentRequest req) {
        MainEquipment entity = mainEquipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Main equipment not found with id: " + id));
        entity.setEquipmentType(req.equipmentType());
        entity.setBrand(req.brand());
        entity.setModel(req.model());
        entity.setSerialNumber(req.serialNumber());
        entity.setCondition(req.condition());
        entity.setStatus(req.status());
        entity.setNotes(req.notes());
        mainEquipmentRepository.save(entity);
        return buildEquipmentListResponse();
    }

    @Transactional
    public EquipmentListResponse deleteMainEquipment(Long id) {
        if (!mainEquipmentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Main equipment not found with id: " + id);
        }
        mainEquipmentRepository.deleteById(id);
        return buildEquipmentListResponse();
    }

    // ── Sub Equipment ─────────────────────────────────────────────────────────

    @Transactional
    public EquipmentListResponse addSubEquipment(SubEquipmentRequest req) {
        SubEquipment entity = SubEquipment.builder()
                .equipmentType(req.equipmentType())
                .brand(req.brand())
                .model(req.model())
                .capacity(req.capacity())
                .totalQuantity(req.totalQuantity())
                .usedQuantity(req.usedQuantity())
                .availableQuantity(req.availableQuantity())
                .notes(req.notes())
                .build();
        subEquipmentRepository.save(entity);
        return buildEquipmentListResponse();
    }

    @Transactional
    public EquipmentListResponse updateSubEquipment(Long id, SubEquipmentRequest req) {
        SubEquipment entity = subEquipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Sub equipment not found with id: " + id));
        entity.setEquipmentType(req.equipmentType());
        entity.setBrand(req.brand());
        entity.setModel(req.model());
        entity.setCapacity(req.capacity());
        entity.setTotalQuantity(req.totalQuantity());
        entity.setUsedQuantity(req.usedQuantity());
        entity.setAvailableQuantity(req.availableQuantity());
        entity.setNotes(req.notes());
        subEquipmentRepository.save(entity);
        return buildEquipmentListResponse();
    }

    @Transactional
    public EquipmentListResponse deleteSubEquipment(Long id) {
        if (!subEquipmentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sub equipment not found with id: " + id);
        }
        subEquipmentRepository.deleteById(id);
        return buildEquipmentListResponse();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private EquipmentListResponse buildEquipmentListResponse() {
        List<MainEquipmentResponse> mainList = mainEquipmentRepository.findAll().stream()
                .map(e -> new MainEquipmentResponse(
                        e.getMainEquipmentId(),
                        e.getEquipmentType(),
                        e.getBrand(),
                        e.getModel(),
                        e.getSerialNumber(),
                        e.getCondition(),
                        e.getStatus(),
                        e.getNotes()
                ))
                .toList();

        List<SubEquipmentResponse> subList = subEquipmentRepository.findAll().stream()
                .map(e -> new SubEquipmentResponse(
                        e.getSubEquipmentId(),
                        e.getEquipmentType(),
                        e.getBrand(),
                        e.getModel(),
                        e.getCapacity(),
                        e.getTotalQuantity(),
                        e.getUsedQuantity(),
                        e.getAvailableQuantity(),
                        e.getNotes()
                ))
                .toList();

        return new EquipmentListResponse(mainList, subList);
    }
}
