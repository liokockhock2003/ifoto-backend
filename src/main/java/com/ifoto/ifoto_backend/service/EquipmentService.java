package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.EquipmentListResponse;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.MainEquipmentRequest;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.MainEquipmentResponse;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.RentableEquipmentResponse;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentRequest;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentResponse;
import com.ifoto.ifoto_backend.model.MainEquipment;
import com.ifoto.ifoto_backend.model.MemberType;
import com.ifoto.ifoto_backend.model.RentalCategory;
import com.ifoto.ifoto_backend.model.SubEquipment;
import com.ifoto.ifoto_backend.repository.MainEquipmentRepository;
import com.ifoto.ifoto_backend.repository.RentalCategoryRepository;
import com.ifoto.ifoto_backend.repository.RentalPricingRepository;
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
    private final RentalCategoryRepository rentalCategoryRepository;
    private final RentalPricingRepository rentalPricingRepository;

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EquipmentListResponse getAllEquipment() {
        return buildEquipmentListResponse();
    }

    // ── Main Equipment ────────────────────────────────────────────────────────

    @Transactional
    public MainEquipmentResponse addMainEquipment(MainEquipmentRequest req) {
        RentalCategory pricingCategory = resolvePricingCategory(req.pricingCategoryId());
        MainEquipment entity = MainEquipment.builder()
                .equipmentType(req.equipmentType())
                .lensType(req.lensType())
                .brand(req.brand())
                .model(req.model())
                .serialNumber(req.serialNumber())
                .condition(req.condition())
                .status(req.status())
                .notes(req.notes())
                .pricingCategory(pricingCategory)
                .isForRent(req.isForRent())
                .build();
        MainEquipment saved = mainEquipmentRepository.save(entity);
        return toMainResponse(saved);
    }

    @Transactional
    public MainEquipmentResponse updateMainEquipment(Long id, MainEquipmentRequest req) {
        MainEquipment entity = mainEquipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Main equipment not found with id: " + id));
        entity.setEquipmentType(req.equipmentType());
        entity.setLensType(req.lensType());
        entity.setBrand(req.brand());
        entity.setModel(req.model());
        entity.setSerialNumber(req.serialNumber());
        entity.setCondition(req.condition());
        entity.setStatus(req.status());
        entity.setNotes(req.notes());
        entity.setPricingCategory(resolvePricingCategory(req.pricingCategoryId()));
        entity.setForRent(req.isForRent());
        return toMainResponse(mainEquipmentRepository.save(entity));
    }

    @Transactional
    public void deleteMainEquipment(Long id) {
        int deleted = mainEquipmentRepository.deleteByIdReturningCount(id);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Main equipment not found with id: " + id);
        }
    }

    // ── Sub Equipment ─────────────────────────────────────────────────────────

    @Transactional
    public SubEquipmentResponse addSubEquipment(SubEquipmentRequest req) {
        SubEquipment entity = SubEquipment.builder()
                .type(req.type())
                .equipmentType(req.equipmentType())
                .cameraModel(req.cameraModel())
                .brand(req.brand())
                .capacity(req.capacity())
                .totalQuantity(req.totalQuantity())
                .usedQuantity(req.usedQuantity())
                .availableQuantity(req.availableQuantity())
                .notes(req.notes())
                .build();
        SubEquipment saved = subEquipmentRepository.save(entity);
        return toSubResponse(saved);
    }

    @Transactional
    public SubEquipmentResponse updateSubEquipment(Long id, SubEquipmentRequest req) {
        SubEquipment entity = subEquipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Sub equipment not found with id: " + id));
        if (req.type() != null) entity.setType(req.type());
        entity.setEquipmentType(req.equipmentType());
        entity.setCameraModel(req.cameraModel());
        entity.setBrand(req.brand());
        entity.setCapacity(req.capacity());
        entity.setTotalQuantity(req.totalQuantity());
        entity.setUsedQuantity(req.usedQuantity());
        entity.setAvailableQuantity(req.availableQuantity());
        entity.setNotes(req.notes());
        return toSubResponse(subEquipmentRepository.save(entity));
    }

    @Transactional
    public void deleteSubEquipment(Long id) {
        int deleted = subEquipmentRepository.deleteByIdReturningCount(id);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sub equipment not found with id: " + id);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private EquipmentListResponse buildEquipmentListResponse() {
        List<MainEquipmentResponse> mainList = mainEquipmentRepository.findAll().stream()
                .map(this::toMainResponse)
                .toList();

        List<SubEquipmentResponse> subList = subEquipmentRepository.findAll().stream()
                .map(this::toSubResponse)
                .toList();

        return new EquipmentListResponse(mainList, subList);
    }

    private MainEquipmentResponse toMainResponse(MainEquipment e) {
        RentalCategory pc = e.getPricingCategory();
        return new MainEquipmentResponse(
                e.getMainEquipmentId(),
                e.getEquipmentType(),
                e.getLensType(),
                e.getBrand(),
                e.getModel(),
                e.getSerialNumber(),
                e.getCondition(),
                e.getStatus(),
                e.getNotes(),
                pc != null ? pc.getId() : null,
                pc != null ? pc.getName() : null,
                e.isForRent()
        );
    }

    @Transactional(readOnly = true)
    public List<RentableEquipmentResponse> getRentableEquipment(MemberType memberType) {
        return mainEquipmentRepository.findByIsForRentTrue().stream()
                .filter(e -> e.getPricingCategory() != null)
                .flatMap(e -> rentalPricingRepository
                        .findByPricingCategory_NameAndMemberType(e.getPricingCategory().getName(), memberType)
                        .map(pricing -> new RentableEquipmentResponse(
                                e.getMainEquipmentId(),
                                e.getEquipmentType(),
                                e.getLensType(),
                                e.getBrand(),
                                e.getModel(),
                                e.getSerialNumber(),
                                e.getCondition(),
                                e.getStatus(),
                                e.getNotes(),
                                e.getPricingCategory().getId(),
                                e.getPricingCategory().getName(),
                                pricing.getMemberType(),
                                pricing.getRate1Day(),
                                pricing.getRate3Days(),
                                pricing.getRatePerDayExtra(),
                                pricing.getLatePenaltyPerDay()
                        ))
                        .stream())
                .toList();
    }

    private RentalCategory resolvePricingCategory(Long pricingCategoryId) {
        if (pricingCategoryId == null) return null;
        return rentalCategoryRepository.findById(pricingCategoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pricing category not found with id: " + pricingCategoryId));
    }

    private SubEquipmentResponse toSubResponse(SubEquipment e) {
        return new SubEquipmentResponse(
                e.getSubEquipmentId(),
                e.getType(),
                e.getEquipmentType(),
                e.getCameraModel(),
                e.getBrand(),
                e.getCapacity(),
                e.getTotalQuantity(),
                e.getUsedQuantity(),
                e.getAvailableQuantity(),
                e.getNotes()
        );
    }
}
