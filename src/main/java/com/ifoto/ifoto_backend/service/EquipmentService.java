package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.*;
import com.ifoto.ifoto_backend.model.*;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import com.ifoto.ifoto_backend.model.enumerator.MainEquipmentStatusType;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final MainEquipmentRepository mainEquipmentRepository;
    private final SubEquipmentRepository subEquipmentRepository;
    private final RentalCategoryRepository rentalCategoryRepository;
    private final EquipmentRentalSubItemRepository equipmentRentalSubItemRepository;
    private final EquipmentRentalItemRepository equipmentRentalItemRepository;
    private final EventEquipmentRequestSubItemRepository eventEquipmentRequestSubItemRepository;
    private final EventEquipmentRequestItemRepository eventEquipmentRequestItemRepository;
    private final MainEquipmentStatusRepository mainEquipmentStatusRepository;
    private final SubEquipmentQuantityHoldRepository subEquipmentQuantityHoldRepository;

    private static final List<RentalStatus> RENTAL_BLOCKING = List.of(
            RentalStatus.APPROVED, RentalStatus.PENDING_PAYMENT,
            RentalStatus.PAID, RentalStatus.ACTIVE, RentalStatus.OVERDUE);

    private static final List<EventEquipmentRequestStatus> EVENT_BLOCKING = List.of(
            EventEquipmentRequestStatus.APPROVED, EventEquipmentRequestStatus.ACTIVE);

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EquipmentListResponse getAllEquipment() {
        LocalDate today = LocalDate.now();

        List<MainEquipment> allMain = mainEquipmentRepository.findAll();
        List<Long> mainIds = allMain.stream().map(MainEquipment::getMainEquipmentId).toList();

        Map<Long, MainEquipmentStatusType> activeStatusMap = mainIds.isEmpty() ? Map.of() :
                mainEquipmentStatusRepository.findActiveByEquipmentIds(mainIds, today)
                        .stream().collect(Collectors.toMap(
                                s -> s.getMainEquipment().getMainEquipmentId(),
                                MainEquipmentStatus::getStatusType,
                                (a, b) -> a));

        Set<Long> rentalBookedIds = mainIds.isEmpty() ? Set.of() :
                new java.util.HashSet<>(equipmentRentalItemRepository
                        .findBookedEquipmentIds(mainIds, today, RENTAL_BLOCKING));

        Set<Long> eventBookedIds = mainIds.isEmpty() ? Set.of() :
                new java.util.HashSet<>(eventEquipmentRequestItemRepository
                        .findBookedEquipmentIds(mainIds, today, List.copyOf(EVENT_BLOCKING)));

        List<MainEquipmentResponse> mainList = allMain.stream()
                .map(e -> {
                    Long id = e.getMainEquipmentId();
                    MainEquipmentStatusType effectiveStatus;
                    if (activeStatusMap.containsKey(id)) {
                        effectiveStatus = activeStatusMap.get(id);
                    } else if (rentalBookedIds.contains(id) || eventBookedIds.contains(id)) {
                        effectiveStatus = MainEquipmentStatusType.BOOKED;
                    } else {
                        effectiveStatus = MainEquipmentStatusType.AVAILABLE;
                    }
                    return toMainResponse(e, effectiveStatus, List.of());
                })
                .toList();

        List<SubEquipment> allSub = subEquipmentRepository.findAll();
        List<Long> subIds = allSub.stream().map(SubEquipment::getSubEquipmentId).toList();

        Map<Long, Integer> rentalCommittedMap = subIds.isEmpty() ? Map.of() :
                equipmentRentalSubItemRepository
                        .sumCommittedQuantityPerSubEquipment(today, today, RENTAL_BLOCKING)
                        .stream().collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> ((Number) row[1]).intValue()));

        Map<Long, Integer> eventCommittedMap = subIds.isEmpty() ? Map.of() :
                eventEquipmentRequestSubItemRepository
                        .sumCommittedQuantityPerSubEquipment(today, today, EVENT_BLOCKING)
                        .stream().collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> ((Number) row[1]).intValue()));

        Map<Long, Integer> holdMap = subIds.isEmpty() ? Map.of() :
                subEquipmentQuantityHoldRepository
                        .sumHeldQuantityPerSubEquipment(today, today)
                        .stream().collect(Collectors.toMap(
                                row -> (Long) row[0],
                                row -> ((Number) row[1]).intValue()));

        List<SubEquipmentResponse> subList = allSub.stream()
                .map(s -> {
                    int committed = rentalCommittedMap.getOrDefault(s.getSubEquipmentId(), 0)
                            + eventCommittedMap.getOrDefault(s.getSubEquipmentId(), 0);
                    int held = holdMap.getOrDefault(s.getSubEquipmentId(), 0);
                    return toSubResponse(s, committed, held, List.of());
                })
                .toList();

        return new EquipmentListResponse(mainList, subList);
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
                .problems(req.problems())
                .pricingCategory(pricingCategory)
                .isForRent(req.isForRent())
                .build();
        return toMainResponse(mainEquipmentRepository.save(entity), MainEquipmentStatusType.AVAILABLE, List.of());
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
        entity.setProblems(req.problems());
        if (req.pricingCategoryId() != null) {
            entity.setPricingCategory(resolvePricingCategory(req.pricingCategoryId()));
        }
        entity.setForRent(req.isForRent());
        MainEquipmentStatusType currentStatus = mainEquipmentStatusRepository
                .findActiveByEquipmentIds(List.of(id), LocalDate.now())
                .stream().findFirst()
                .map(MainEquipmentStatus::getStatusType)
                .orElse(MainEquipmentStatusType.AVAILABLE);
        List<MainEquipmentStatusResponse> statuses = mainEquipmentStatusRepository
                .findUpcomingByEquipmentIds(List.of(id), LocalDate.now())
                .stream().map(this::toStatusResponse).toList();
        return toMainResponse(mainEquipmentRepository.save(entity), currentStatus, statuses);
    }

    @Transactional
    public void deleteMainEquipment(Long id) {
        MainEquipment entity = mainEquipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Main equipment not found with id: " + id));
        mainEquipmentRepository.delete(entity);
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
                .notes(req.notes())
                .pricingCategory(resolvePricingCategory(req.pricingCategoryId()))
                .isForRent(req.isForRent())
                .build();
        return toSubResponse(subEquipmentRepository.save(entity), 0, 0, List.of());
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
        entity.setNotes(req.notes());
        entity.setPricingCategory(resolvePricingCategory(req.pricingCategoryId()));
        entity.setForRent(req.isForRent());
        List<SubEquipmentQuantityHoldResponse> holds = subEquipmentQuantityHoldRepository
                .findUpcomingBySubEquipmentIds(List.of(id), LocalDate.now())
                .stream().map(this::toHoldResponse).toList();
        return toSubResponse(subEquipmentRepository.save(entity), 0, 0, holds);
    }

    @Transactional
    public void deleteSubEquipment(Long id) {
        int deleted = subEquipmentRepository.deleteByIdReturningCount(id);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sub equipment not found with id: " + id);
        }
    }

    // ── Main Equipment Status CRUD ────────────────────────────────────────────

    @Transactional
    public MainEquipmentStatusResponse addMainEquipmentStatus(Long equipmentId, MainEquipmentStatusRequest req) {
        if (req.statusType() == MainEquipmentStatusType.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "AVAILABLE is the default state and cannot be stored explicitly");
        }
        MainEquipment equipment = mainEquipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Main equipment not found: " + equipmentId));
        if (mainEquipmentStatusRepository.existsConflictingStatus(equipmentId, req.startDate(), req.endDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A status entry already exists that overlaps the requested date range");
        }
        if (equipmentRentalItemRepository.existsConflictingApprovedRental(
                equipmentId, req.startDate(), req.endDate(), 0L, RENTAL_BLOCKING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Equipment has an active rental booking in the requested date range");
        }
        if (eventEquipmentRequestItemRepository.existsConflictingRequest(
                equipmentId, 0L, List.copyOf(EVENT_BLOCKING), req.startDate(), req.endDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Equipment has an active event request in the requested date range");
        }
        MainEquipmentStatus status = MainEquipmentStatus.builder()
                .mainEquipment(equipment)
                .statusType(req.statusType())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .notes(req.notes())
                .build();
        return toStatusResponse(mainEquipmentStatusRepository.save(status));
    }

    @Transactional(readOnly = true)
    public List<MainEquipmentStatusResponse> getMainEquipmentStatuses(Long equipmentId) {
        if (!mainEquipmentRepository.existsById(equipmentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Main equipment not found: " + equipmentId);
        }
        return mainEquipmentStatusRepository
                .findByMainEquipmentMainEquipmentIdOrderByStartDateAsc(equipmentId)
                .stream().map(this::toStatusResponse).toList();
    }

    @Transactional
    public MainEquipmentStatusResponse updateMainEquipmentStatus(Long equipmentId, Long statusId,
            MainEquipmentStatusRequest req) {
        if (req.statusType() == MainEquipmentStatusType.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "AVAILABLE is the default state and cannot be stored explicitly");
        }
        MainEquipmentStatus status = mainEquipmentStatusRepository.findById(statusId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Status entry not found: " + statusId));
        if (!status.getMainEquipment().getMainEquipmentId().equals(equipmentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Status entry not found: " + statusId);
        }
        if (mainEquipmentStatusRepository.existsConflictingStatusExcluding(
                equipmentId, statusId, req.startDate(), req.endDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A status entry already exists that overlaps the requested date range");
        }
        if (equipmentRentalItemRepository.existsConflictingApprovedRental(
                equipmentId, req.startDate(), req.endDate(), 0L, RENTAL_BLOCKING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Equipment has an active rental booking in the requested date range");
        }
        if (eventEquipmentRequestItemRepository.existsConflictingRequest(
                equipmentId, 0L, List.copyOf(EVENT_BLOCKING), req.startDate(), req.endDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Equipment has an active event request in the requested date range");
        }
        status.setStatusType(req.statusType());
        status.setStartDate(req.startDate());
        status.setEndDate(req.endDate());
        status.setNotes(req.notes());
        return toStatusResponse(mainEquipmentStatusRepository.save(status));
    }

    @Transactional
    public void deleteMainEquipmentStatus(Long equipmentId, Long statusId) {
        MainEquipmentStatus status = mainEquipmentStatusRepository.findById(statusId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Status entry not found: " + statusId));
        if (!status.getMainEquipment().getMainEquipmentId().equals(equipmentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Status entry not found: " + statusId);
        }
        mainEquipmentStatusRepository.delete(status);
    }

    // ── Sub Equipment Quantity Hold CRUD ──────────────────────────────────────

    @Transactional
    public SubEquipmentQuantityHoldResponse addQuantityHold(Long subEquipmentId,
            SubEquipmentQuantityHoldRequest req) {
        SubEquipment sub = subEquipmentRepository.findById(subEquipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Sub equipment not found: " + subEquipmentId));
        int existingHolds = subEquipmentQuantityHoldRepository.sumHeldQuantity(
                subEquipmentId, req.startDate(), req.endDate(), 0L);
        int rentalCommitted = equipmentRentalSubItemRepository.sumCommittedQuantity(
                subEquipmentId, req.startDate(), req.endDate(), RENTAL_BLOCKING);
        int eventCommitted = eventEquipmentRequestSubItemRepository.sumCommittedQuantity(
                subEquipmentId, req.startDate(), req.endDate(), EVENT_BLOCKING, 0L);
        int totalAllocated = existingHolds + rentalCommitted + eventCommitted;
        if (totalAllocated + req.quantity() > sub.getTotalQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hold quantity (" + req.quantity() + ") plus existing allocations (holds="
                            + existingHolds + ", rentals=" + rentalCommitted + ", events=" + eventCommitted
                            + ") exceeds total quantity (" + sub.getTotalQuantity() + ")");
        }
        SubEquipmentQuantityHold hold = SubEquipmentQuantityHold.builder()
                .subEquipment(sub)
                .quantity(req.quantity())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .notes(req.notes())
                .build();
        return toHoldResponse(subEquipmentQuantityHoldRepository.save(hold));
    }

    @Transactional(readOnly = true)
    public List<SubEquipmentQuantityHoldResponse> getQuantityHolds(Long subEquipmentId) {
        if (!subEquipmentRepository.existsById(subEquipmentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sub equipment not found: " + subEquipmentId);
        }
        return subEquipmentQuantityHoldRepository
                .findBySubEquipmentSubEquipmentIdOrderByStartDateAsc(subEquipmentId)
                .stream().map(this::toHoldResponse).toList();
    }

    @Transactional
    public SubEquipmentQuantityHoldResponse updateQuantityHold(Long subEquipmentId, Long holdId,
            SubEquipmentQuantityHoldRequest req) {
        SubEquipmentQuantityHold hold = subEquipmentQuantityHoldRepository.findById(holdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Quantity hold not found: " + holdId));
        if (!hold.getSubEquipment().getSubEquipmentId().equals(subEquipmentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quantity hold not found: " + holdId);
        }
        SubEquipment sub = hold.getSubEquipment();
        int existingHolds = subEquipmentQuantityHoldRepository.sumHeldQuantity(
                subEquipmentId, req.startDate(), req.endDate(), holdId);
        int rentalCommitted = equipmentRentalSubItemRepository.sumCommittedQuantity(
                subEquipmentId, req.startDate(), req.endDate(), RENTAL_BLOCKING);
        int eventCommitted = eventEquipmentRequestSubItemRepository.sumCommittedQuantity(
                subEquipmentId, req.startDate(), req.endDate(), EVENT_BLOCKING, 0L);
        int totalAllocated = existingHolds + rentalCommitted + eventCommitted;
        if (totalAllocated + req.quantity() > sub.getTotalQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hold quantity (" + req.quantity() + ") plus existing allocations (holds="
                            + existingHolds + ", rentals=" + rentalCommitted + ", events=" + eventCommitted
                            + ") exceeds total quantity (" + sub.getTotalQuantity() + ")");
        }
        hold.setQuantity(req.quantity());
        hold.setStartDate(req.startDate());
        hold.setEndDate(req.endDate());
        hold.setNotes(req.notes());
        return toHoldResponse(subEquipmentQuantityHoldRepository.save(hold));
    }

    @Transactional
    public void deleteQuantityHold(Long subEquipmentId, Long holdId) {
        SubEquipmentQuantityHold hold = subEquipmentQuantityHoldRepository.findById(holdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Quantity hold not found: " + holdId));
        if (!hold.getSubEquipment().getSubEquipmentId().equals(subEquipmentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quantity hold not found: " + holdId);
        }
        subEquipmentQuantityHoldRepository.delete(hold);
    }

    // ── Available equipment ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EquipmentListResponse getAvailableEquipment(LocalDate startDate, LocalDate endDate, String context) {
        boolean isRental = "RENTAL".equalsIgnoreCase(context);

        // ── Main equipment: single DB query with NOT EXISTS — no Java-side filtering ──
        List<MainEquipmentResponse> mainList = mainEquipmentRepository
                .findAvailableEquipment(isRental, startDate, endDate, RENTAL_BLOCKING, EVENT_BLOCKING)
                .stream()
                .map(e -> toMainResponse(e, MainEquipmentStatusType.AVAILABLE, List.of()))
                .toList();

        // ── Sub-equipment: context-filtered, batch-sum all 3 commitment sources ──
        List<SubEquipment> subCandidates = isRental
                ? subEquipmentRepository.findByIsForRentTrue()
                : subEquipmentRepository.findAll();
        List<Long> subIds = subCandidates.stream().map(SubEquipment::getSubEquipmentId).toList();

        Map<Long, Integer> rentalCommittedMap = subIds.isEmpty() ? Map.of() :
                equipmentRentalSubItemRepository
                        .sumCommittedQuantityPerSubEquipment(startDate, endDate, RENTAL_BLOCKING)
                        .stream().collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));

        Map<Long, Integer> eventCommittedMap = subIds.isEmpty() ? Map.of() :
                eventEquipmentRequestSubItemRepository
                        .sumCommittedQuantityPerSubEquipment(startDate, endDate, EVENT_BLOCKING)
                        .stream().collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));

        Map<Long, Integer> holdMap = subIds.isEmpty() ? Map.of() :
                subEquipmentQuantityHoldRepository
                        .sumHeldQuantityPerSubEquipment(startDate, endDate)
                        .stream().collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));

        List<SubEquipmentResponse> subList = subCandidates.stream()
                .map(s -> {
                    int rentalCommitted = rentalCommittedMap.getOrDefault(s.getSubEquipmentId(), 0);
                    int eventCommitted  = eventCommittedMap.getOrDefault(s.getSubEquipmentId(), 0);
                    int held            = holdMap.getOrDefault(s.getSubEquipmentId(), 0);
                    return toSubResponse(s, rentalCommitted + eventCommitted + held, 0, List.of());
                })
                .toList();

        return new EquipmentListResponse(mainList, subList);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private MainEquipmentResponse toMainResponse(MainEquipment e, MainEquipmentStatusType effectiveStatus,
            List<MainEquipmentStatusResponse> statuses) {
        RentalCategory pc = e.getPricingCategory();
        return new MainEquipmentResponse(
                e.getMainEquipmentId(),
                e.getEquipmentType(),
                e.getLensType(),
                e.getBrand(),
                e.getModel(),
                e.getSerialNumber(),
                e.getCondition(),
                effectiveStatus.name(),  // always computed from main_equipment_statuses
                e.getProblems(),
                pc != null ? pc.getId() : null,
                pc != null ? pc.getName() : null,
                e.isForRent(),
                statuses
        );
    }

    private SubEquipmentResponse toSubResponse(SubEquipment e, int committed, int held,
            List<SubEquipmentQuantityHoldResponse> holds) {
        RentalCategory pc = e.getPricingCategory();
        return new SubEquipmentResponse(
                e.getSubEquipmentId(),
                e.getType(),
                e.getEquipmentType(),
                e.getCameraModel(),
                e.getBrand(),
                e.getCapacity(),
                e.getTotalQuantity(),
                committed,
                held,
                Math.max(0, e.getTotalQuantity() - committed - held),
                e.getNotes(),
                pc != null ? pc.getId() : null,
                pc != null ? pc.getName() : null,
                e.isForRent(),
                holds
        );
    }

    private MainEquipmentStatusResponse toStatusResponse(MainEquipmentStatus s) {
        return new MainEquipmentStatusResponse(s.getId(), s.getStatusType(), s.getStartDate(),
                s.getEndDate(), s.getNotes());
    }

    private SubEquipmentQuantityHoldResponse toHoldResponse(SubEquipmentQuantityHold h) {
        return new SubEquipmentQuantityHoldResponse(h.getId(), h.getQuantity(), h.getStartDate(),
                h.getEndDate(), h.getNotes());
    }

    private RentalCategory resolvePricingCategory(Long pricingCategoryId) {
        if (pricingCategoryId == null) return null;
        return rentalCategoryRepository.findById(pricingCategoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pricing category not found with id: " + pricingCategoryId));
    }
}
