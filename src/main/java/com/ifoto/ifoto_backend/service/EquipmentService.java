package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.*;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.EquipmentBoundaryNote;
import com.ifoto.ifoto_backend.model.*;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import com.ifoto.ifoto_backend.model.enumerator.MainEquipmentStatusType;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
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

        @Value("${app.rental.buffer-minutes:60}")
        private int bufferMinutes;

        private static final List<RentalStatus> RENTAL_BLOCKING = List.of(
                        RentalStatus.APPROVED, RentalStatus.PICKED_UP, RentalStatus.PENDING_PAYMENT,
                        RentalStatus.PAID, RentalStatus.ACTIVE, RentalStatus.OVERDUE);

        private static final List<EventEquipmentRequestStatus> EVENT_BLOCKING = List.of(
                        EventEquipmentRequestStatus.APPROVED, EventEquipmentRequestStatus.ACTIVE);

        // ── Read ──────────────────────────────────────────────────────────────────

        @Transactional(readOnly = true)
        public EquipmentListResponse getAllEquipment() {
                LocalDateTime now = LocalDateTime.now();

                List<MainEquipment> allMain = mainEquipmentRepository.findAll();
                List<Long> mainIds = allMain.stream().map(MainEquipment::getMainEquipmentId).toList();

                Map<Long, MainEquipmentStatusType> activeStatusMap = mainIds.isEmpty() ? Map.of()
                                : mainEquipmentStatusRepository.findActiveByEquipmentIds(mainIds, now)
                                                .stream().collect(Collectors.toMap(
                                                                s -> s.getMainEquipment().getMainEquipmentId(),
                                                                MainEquipmentStatus::getStatusType,
                                                                (a, b) -> a));

                Set<Long> rentalBookedIds = mainIds.isEmpty() ? Set.of()
                                : new HashSet<>(equipmentRentalItemRepository
                                                .findBookedEquipmentIds(mainIds, now.toLocalDate(), RENTAL_BLOCKING));

                Set<Long> eventBookedIds = mainIds.isEmpty() ? Set.of()
                                : new HashSet<>(eventEquipmentRequestItemRepository
                                                .findBookedEquipmentIds(mainIds, now, List.copyOf(EVENT_BLOCKING)));

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
                                        return toMainResponse(e, effectiveStatus, List.of(), List.of());
                                })
                                .toList();

                List<SubEquipment> allSub = subEquipmentRepository.findAll();
                List<Long> subIds = allSub.stream().map(SubEquipment::getSubEquipmentId).toList();

                SubQuantityMaps subQty = buildSubQuantityMaps(subIds, now, now);

                List<SubEquipmentResponse> subList = allSub.stream()
                                .map(s -> {
                                        int committed = subQty.rental().getOrDefault(s.getSubEquipmentId(), 0)
                                                        + subQty.event().getOrDefault(s.getSubEquipmentId(), 0);
                                        int held = subQty.hold().getOrDefault(s.getSubEquipmentId(), 0);
                                        return toSubResponse(s, committed, held, List.of(), List.of());
                                })
                                .toList();

                return new EquipmentListResponse(mainList, subList);
        }

        @Transactional(readOnly = true)
        public EquipmentListResponse getAllEquipment(LocalDate startDate, LocalDate endDate) {
                return getAllEquipment(startDate, endDate, 0L);
        }

        @Transactional(readOnly = true)
        public EquipmentListResponse getAllEquipment(LocalDate startDate, LocalDate endDate, Long excludeRentalId) {
                LocalDateTime effectiveStart = startDate.atStartOfDay().minusMinutes(bufferMinutes);
                LocalDateTime effectiveEnd = endDate.atTime(23, 59, 59).plusMinutes(bufferMinutes);

                // ── Main equipment ──
                List<MainEquipment> allMain = mainEquipmentRepository.findAll();
                List<Long> mainIds = allMain.stream().map(MainEquipment::getMainEquipmentId).toList();

                Set<Long> fullyAvailableIds = mainIds.isEmpty() ? Set.of()
                                : mainEquipmentRepository
                                                .findAvailableEquipment(false, startDate, endDate, RENTAL_BLOCKING,
                                                                EVENT_BLOCKING,
                                                                effectiveStart, effectiveEnd, excludeRentalId)
                                                .stream().map(MainEquipment::getMainEquipmentId)
                                                .collect(Collectors.toSet());

                Map<Long, MainEquipmentStatusType> adminStatusMap = mainIds.isEmpty() ? Map.of()
                                : mainEquipmentStatusRepository
                                                .findStatusTypesForRange(mainIds, effectiveStart, effectiveEnd)
                                                .stream().collect(Collectors.toMap(
                                                                row -> (Long) row[0],
                                                                row -> (MainEquipmentStatusType) row[1],
                                                                (a, b) -> a));

                List<Long> partialCandidateIds = allMain.stream()
                                .map(MainEquipment::getMainEquipmentId)
                                .filter(id -> !fullyAvailableIds.contains(id))
                                .toList();

                Map<Long, MainEquipmentStatusType> computedStatusMap = new HashMap<>();
                Map<Long, List<EquipmentBoundaryNote>> mainBoundaryMap = new HashMap<>();

                if (!partialCandidateIds.isEmpty()) {
                        PartialCandidateResult partial = analyzePartialCandidates(
                                        partialCandidateIds, startDate, endDate, effectiveStart, effectiveEnd,
                                        excludeRentalId);
                        for (Map.Entry<Long, MainEquipmentStatusType> entry : partial.statusMap().entrySet()) {
                                Long id = entry.getKey();
                                MainEquipmentStatusType status = entry.getValue();
                                computedStatusMap.put(id, status == MainEquipmentStatusType.BOOKED
                                                ? adminStatusMap.getOrDefault(id, MainEquipmentStatusType.BOOKED)
                                                : status);
                        }
                        mainBoundaryMap.putAll(partial.boundaryMap());
                }

                List<MainEquipmentResponse> mainList = allMain.stream()
                                .map(e -> {
                                        Long id = e.getMainEquipmentId();
                                        MainEquipmentStatusType status = fullyAvailableIds.contains(id)
                                                        ? MainEquipmentStatusType.AVAILABLE
                                                        : computedStatusMap.getOrDefault(id,
                                                                        MainEquipmentStatusType.BOOKED);
                                        return toMainResponse(e, status, List.of(),
                                                        mainBoundaryMap.getOrDefault(id, List.of()));
                                })
                                .toList();

                // ── Sub-equipment ──
                List<SubEquipment> allSub = subEquipmentRepository.findAll();
                List<Long> subIds = allSub.stream().map(SubEquipment::getSubEquipmentId).toList();

                SubQuantityMaps subQty = buildSubQuantityMaps(subIds, effectiveStart, effectiveEnd, excludeRentalId);
                Map<Long, List<SubEquipmentBoundaryNote>> subBoundaryMap = buildSubBoundaryNotes(subIds, startDate,
                                endDate, excludeRentalId);

                List<SubEquipmentResponse> subList = allSub.stream()
                                .map(s -> {
                                        int committed = subQty.rental().getOrDefault(s.getSubEquipmentId(), 0)
                                                        + subQty.event().getOrDefault(s.getSubEquipmentId(), 0);
                                        int held = subQty.hold().getOrDefault(s.getSubEquipmentId(), 0);
                                        List<SubEquipmentBoundaryNote> rawNotes = subBoundaryMap
                                                        .getOrDefault(s.getSubEquipmentId(), List.of());
                                        List<SubEquipmentBoundaryNote> notes = mergeSubBoundaryNotes(
                                                        filterValidSubBoundaryNotes(rawNotes, startDate, endDate));
                                        return toSubResponse(s, committed + held, 0, List.of(), notes);
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
                                .isForRent(Boolean.TRUE.equals(req.isForRent()))
                                .build();
                return toMainResponse(mainEquipmentRepository.save(entity), MainEquipmentStatusType.AVAILABLE,
                                List.of(), List.of());
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
                if (req.isForRent() != null) {
                        entity.setForRent(req.isForRent());
                }
                LocalDateTime now = LocalDateTime.now();
                MainEquipmentStatusType currentStatus = mainEquipmentStatusRepository
                                .findActiveByEquipmentIds(List.of(id), now)
                                .stream().findFirst()
                                .map(MainEquipmentStatus::getStatusType)
                                .orElse(MainEquipmentStatusType.AVAILABLE);
                List<MainEquipmentStatusResponse> statuses = mainEquipmentStatusRepository
                                .findUpcomingByEquipmentIds(List.of(id), now)
                                .stream().map(this::toStatusResponse).toList();
                return toMainResponse(mainEquipmentRepository.save(entity), currentStatus, statuses, List.of());
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
                                .isForRent(Boolean.TRUE.equals(req.isForRent()))
                                .build();
                return toSubResponse(subEquipmentRepository.save(entity), 0, 0, List.of(), List.of());
        }

        @Transactional
        public SubEquipmentResponse updateSubEquipment(Long id, SubEquipmentRequest req) {
                SubEquipment entity = subEquipmentRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Sub equipment not found with id: " + id));
                if (req.type() != null)
                        entity.setType(req.type());
                entity.setEquipmentType(req.equipmentType());
                entity.setCameraModel(req.cameraModel());
                entity.setBrand(req.brand());
                entity.setCapacity(req.capacity());
                entity.setTotalQuantity(req.totalQuantity());
                entity.setNotes(req.notes());
                if (req.pricingCategoryId() != null) {
                        entity.setPricingCategory(resolvePricingCategory(req.pricingCategoryId()));
                }
                if (req.isForRent() != null) {
                        entity.setForRent(req.isForRent());
                }
                List<SubEquipmentQuantityHoldResponse> holds = subEquipmentQuantityHoldRepository
                                .findUpcomingBySubEquipmentIds(List.of(id), LocalDateTime.now())
                                .stream().map(this::toHoldResponse).toList();
                return toSubResponse(subEquipmentRepository.save(entity), 0, 0, holds, List.of());
        }

        @Transactional
        public void deleteSubEquipment(Long id) {
                SubEquipment entity = subEquipmentRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Sub equipment not found with id: " + id));
                subEquipmentRepository.delete(entity);
        }

        // ── Main Equipment Status CRUD ────────────────────────────────────────────

        @Transactional
        public MainEquipmentStatusResponse addMainEquipmentStatus(Long equipmentId, MainEquipmentStatusRequest req) {
                if (req.statusType() == MainEquipmentStatusType.AVAILABLE
                                || req.statusType() == MainEquipmentStatusType.PARTIALLY_AVAILABLE) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "AVAILABLE and PARTIALLY_AVAILABLE are computed states and cannot be stored explicitly");
                }
                MainEquipment equipment = mainEquipmentRepository.findById(equipmentId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Main equipment not found: " + equipmentId));

                LocalDateTime effectiveStart = req.startDatetime().minusMinutes(bufferMinutes);
                LocalDateTime effectiveEnd = req.endDatetime().plusMinutes(bufferMinutes);

                if (mainEquipmentStatusRepository.existsConflictingStatus(
                                equipmentId, req.startDatetime(), req.endDatetime())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "A status entry already exists that overlaps the requested datetime range");
                }
                checkEquipmentBookingConflicts(equipmentId, effectiveStart, effectiveEnd);
                MainEquipmentStatus status = MainEquipmentStatus.builder()
                                .mainEquipment(equipment)
                                .statusType(req.statusType())
                                .startDatetime(req.startDatetime())
                                .endDatetime(req.endDatetime())
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
                                .findByMainEquipmentMainEquipmentIdOrderByStartDatetimeAsc(equipmentId)
                                .stream().map(this::toStatusResponse).toList();
        }

        @Transactional
        public MainEquipmentStatusResponse updateMainEquipmentStatus(Long equipmentId, Long statusId,
                        MainEquipmentStatusRequest req) {
                if (req.statusType() == MainEquipmentStatusType.AVAILABLE
                                || req.statusType() == MainEquipmentStatusType.PARTIALLY_AVAILABLE) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "AVAILABLE and PARTIALLY AVAILABLE are computed states and cannot be stored explicitly");
                }
                MainEquipmentStatus status = mainEquipmentStatusRepository.findById(statusId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Status entry not found: " + statusId));
                if (!status.getMainEquipment().getMainEquipmentId().equals(equipmentId)) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Status entry not found: " + statusId);
                }

                LocalDateTime effectiveStart = req.startDatetime().minusMinutes(bufferMinutes);
                LocalDateTime effectiveEnd = req.endDatetime().plusMinutes(bufferMinutes);

                if (mainEquipmentStatusRepository.existsConflictingStatusExcluding(
                                equipmentId, statusId, req.startDatetime(), req.endDatetime())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "A status entry already exists that overlaps the requested datetime range");
                }
                checkEquipmentBookingConflicts(equipmentId, effectiveStart, effectiveEnd);
                status.setStatusType(req.statusType());
                status.setStartDatetime(req.startDatetime());
                status.setEndDatetime(req.endDatetime());
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

                LocalDateTime effectiveStart = req.startDatetime().minusMinutes(bufferMinutes);
                LocalDateTime effectiveEnd = req.endDatetime().plusMinutes(bufferMinutes);

                int existingHolds = subEquipmentQuantityHoldRepository.sumHeldQuantity(
                                subEquipmentId, req.startDatetime(), req.endDatetime(), 0L);
                int rentalCommitted = equipmentRentalSubItemRepository.sumCommittedQuantity(
                                subEquipmentId, effectiveStart, effectiveEnd, RENTAL_BLOCKING);
                int eventCommitted = eventEquipmentRequestSubItemRepository.sumCommittedQuantity(
                                subEquipmentId, effectiveStart, effectiveEnd, EVENT_BLOCKING, 0L);
                int totalAllocated = existingHolds + rentalCommitted + eventCommitted;
                if (totalAllocated + req.quantity() > sub.getTotalQuantity()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Hold quantity (" + req.quantity() + ") plus existing allocations (holds="
                                                        + existingHolds + ", rentals=" + rentalCommitted + ", events="
                                                        + eventCommitted
                                                        + ") exceeds total quantity (" + sub.getTotalQuantity() + ")");
                }
                SubEquipmentQuantityHold hold = SubEquipmentQuantityHold.builder()
                                .subEquipment(sub)
                                .quantity(req.quantity())
                                .startDatetime(req.startDatetime())
                                .endDatetime(req.endDatetime())
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
                                .findBySubEquipmentSubEquipmentIdOrderByStartDatetimeAsc(subEquipmentId)
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

                LocalDateTime effectiveStart = req.startDatetime().minusMinutes(bufferMinutes);
                LocalDateTime effectiveEnd = req.endDatetime().plusMinutes(bufferMinutes);

                int existingHolds = subEquipmentQuantityHoldRepository.sumHeldQuantity(
                                subEquipmentId, req.startDatetime(), req.endDatetime(), holdId);
                int rentalCommitted = equipmentRentalSubItemRepository.sumCommittedQuantity(
                                subEquipmentId, effectiveStart, effectiveEnd, RENTAL_BLOCKING);
                int eventCommitted = eventEquipmentRequestSubItemRepository.sumCommittedQuantity(
                                subEquipmentId, effectiveStart, effectiveEnd, EVENT_BLOCKING, 0L);
                int totalAllocated = existingHolds + rentalCommitted + eventCommitted;
                if (totalAllocated + req.quantity() > sub.getTotalQuantity()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Hold quantity (" + req.quantity() + ") plus existing allocations (holds="
                                                        + existingHolds + ", rentals=" + rentalCommitted + ", events="
                                                        + eventCommitted
                                                        + ") exceeds total quantity (" + sub.getTotalQuantity() + ")");
                }
                hold.setQuantity(req.quantity());
                hold.setStartDatetime(req.startDatetime());
                hold.setEndDatetime(req.endDatetime());
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
                return getAvailableEquipment(startDate, endDate, context, 0L);
        }

        @Transactional(readOnly = true)
        public EquipmentListResponse getAvailableEquipment(LocalDate startDate, LocalDate endDate, String context,
                        Long excludeRentalId) {
                boolean isRental = "RENTAL".equalsIgnoreCase(context);

                LocalDateTime effectiveStart = startDate.atStartOfDay().minusMinutes(bufferMinutes);
                LocalDateTime effectiveEnd = endDate.atTime(23, 59, 59).plusMinutes(bufferMinutes);

                // ── Fully available main equipment ──
                List<MainEquipment> fullyAvailable = mainEquipmentRepository
                                .findAvailableEquipment(isRental, startDate, endDate, RENTAL_BLOCKING, EVENT_BLOCKING,
                                                effectiveStart, effectiveEnd, excludeRentalId);
                Set<Long> fullyAvailableIds = fullyAvailable.stream()
                                .map(MainEquipment::getMainEquipmentId).collect(Collectors.toSet());

                List<MainEquipmentResponse> fullyAvailableList = fullyAvailable.stream()
                                .map(e -> toMainResponse(e, MainEquipmentStatusType.AVAILABLE, List.of(), List.of()))
                                .toList();

                // ── Partially available main equipment ──
                List<MainEquipment> allMain = isRental
                                ? mainEquipmentRepository.findByIsForRentTrue()
                                : mainEquipmentRepository.findAll();
                List<Long> partialCandidateIds = allMain.stream()
                                .map(MainEquipment::getMainEquipmentId)
                                .filter(id -> !fullyAvailableIds.contains(id))
                                .toList();

                List<MainEquipmentResponse> partialList = List.of();
                if (!partialCandidateIds.isEmpty()) {
                        PartialCandidateResult partial = analyzePartialCandidates(
                                        partialCandidateIds, startDate, endDate, effectiveStart, effectiveEnd,
                                        excludeRentalId);
                        Map<Long, MainEquipment> candidateMap = allMain.stream()
                                        .collect(Collectors.toMap(MainEquipment::getMainEquipmentId, e -> e));
                        partialList = partialCandidateIds.stream()
                                        .filter(id -> partial.statusMap()
                                                        .get(id) == MainEquipmentStatusType.PARTIALLY_AVAILABLE)
                                        .map(id -> toMainResponse(candidateMap.get(id),
                                                        MainEquipmentStatusType.PARTIALLY_AVAILABLE,
                                                        List.of(), partial.boundaryMap().getOrDefault(id, List.of())))
                                        .toList();
                }

                List<MainEquipmentResponse> mainList = new ArrayList<>(fullyAvailableList);
                mainList.addAll(partialList);

                // ── Sub-equipment ──
                List<SubEquipment> subCandidates = isRental
                                ? subEquipmentRepository.findByIsForRentTrue()
                                : subEquipmentRepository.findAll();
                List<Long> subIds = subCandidates.stream().map(SubEquipment::getSubEquipmentId).toList();

                SubQuantityMaps subQty = buildSubQuantityMaps(subIds, effectiveStart, effectiveEnd, excludeRentalId);
                Map<Long, List<SubEquipmentBoundaryNote>> boundaryNotesMap = buildSubBoundaryNotes(subIds, startDate,
                                endDate, excludeRentalId);

                List<SubEquipmentResponse> subList = subCandidates.stream()
                                .map(s -> {
                                        int committed = subQty.rental().getOrDefault(s.getSubEquipmentId(), 0)
                                                        + subQty.event().getOrDefault(s.getSubEquipmentId(), 0)
                                                        + subQty.hold().getOrDefault(s.getSubEquipmentId(), 0);
                                        List<SubEquipmentBoundaryNote> rawNotes = boundaryNotesMap
                                                        .getOrDefault(s.getSubEquipmentId(), List.of());
                                        List<SubEquipmentBoundaryNote> notes = mergeSubBoundaryNotes(
                                                        filterValidSubBoundaryNotes(rawNotes, startDate, endDate));
                                        return toSubResponse(s, committed, 0, List.of(), notes);
                                })
                                .toList();

                return new EquipmentListResponse(mainList, subList);
        }

        // ── Private helpers ───────────────────────────────────────────────────────

        private MainEquipmentResponse toMainResponse(MainEquipment e, MainEquipmentStatusType effectiveStatus,
                        List<MainEquipmentStatusResponse> statuses,
                        List<EquipmentBoundaryNote> boundaryNotes) {
                RentalCategory pc = e.getPricingCategory();
                return new MainEquipmentResponse(
                                e.getMainEquipmentId(),
                                e.getEquipmentType(),
                                e.getLensType(),
                                e.getBrand(),
                                e.getModel(),
                                e.getSerialNumber(),
                                e.getCondition(),
                                effectiveStatus.name(),
                                e.getProblems(),
                                pc != null ? pc.getId() : null,
                                pc != null ? pc.getName() : null,
                                e.isForRent(),
                                statuses,
                                boundaryNotes);
        }

        private SubEquipmentResponse toSubResponse(SubEquipment e, int committed, int held,
                        List<SubEquipmentQuantityHoldResponse> holds, List<SubEquipmentBoundaryNote> boundaryNotes) {
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
                                holds,
                                boundaryNotes);
        }

        private MainEquipmentStatusResponse toStatusResponse(MainEquipmentStatus s) {
                return new MainEquipmentStatusResponse(s.getId(), s.getStatusType(),
                                s.getStartDatetime(), s.getEndDatetime(), s.getNotes());
        }

        private SubEquipmentQuantityHoldResponse toHoldResponse(SubEquipmentQuantityHold h) {
                return new SubEquipmentQuantityHoldResponse(h.getId(), h.getQuantity(),
                                h.getStartDatetime(), h.getEndDatetime(), h.getNotes());
        }

        private List<EquipmentBoundaryNote> mergeBoundaryNotes(List<EquipmentBoundaryNote> notes) {
                Map<Long, EquipmentBoundaryNote> byRental = new LinkedHashMap<>();
                Map<Long, EquipmentBoundaryNote> byStatus = new LinkedHashMap<>();
                Map<Long, EquipmentBoundaryNote> byEventRequest = new LinkedHashMap<>();
                for (EquipmentBoundaryNote n : notes) {
                        if (n.rentalId() != null) {
                                byRental.merge(n.rentalId(), n, (a, b) -> new EquipmentBoundaryNote(
                                                a.rentalId(), null, null,
                                                a.availableAfter() != null ? a.availableAfter() : b.availableAfter(),
                                                a.mustReturnBefore() != null ? a.mustReturnBefore()
                                                                : b.mustReturnBefore()));
                        } else if (n.statusId() != null) {
                                byStatus.merge(n.statusId(), n, (a, b) -> new EquipmentBoundaryNote(
                                                null, a.statusId(), null,
                                                a.availableAfter() != null ? a.availableAfter() : b.availableAfter(),
                                                a.mustReturnBefore() != null ? a.mustReturnBefore()
                                                                : b.mustReturnBefore()));
                        } else if (n.eventRequestId() != null) {
                                byEventRequest.merge(n.eventRequestId(), n, (a, b) -> new EquipmentBoundaryNote(
                                                null, null, a.eventRequestId(),
                                                a.availableAfter() != null ? a.availableAfter() : b.availableAfter(),
                                                a.mustReturnBefore() != null ? a.mustReturnBefore()
                                                                : b.mustReturnBefore()));
                        }
                }
                List<EquipmentBoundaryNote> result = new ArrayList<>();
                result.addAll(byRental.values());
                result.addAll(byStatus.values());
                result.addAll(byEventRequest.values());
                return result;
        }

        private List<SubEquipmentBoundaryNote> filterValidSubBoundaryNotes(
                        List<SubEquipmentBoundaryNote> notes, LocalDate startDate, LocalDate endDate) {
                return notes.stream()
                                .filter(n -> (n.availableAfter() == null
                                                || !n.availableAfter().toLocalDate().isAfter(startDate))
                                                && (n.mustReturnBefore() == null
                                                                || !n.mustReturnBefore().toLocalDate()
                                                                                .isBefore(endDate)))
                                .toList();
        }

        private List<SubEquipmentBoundaryNote> mergeSubBoundaryNotes(List<SubEquipmentBoundaryNote> notes) {
                Map<Long, SubEquipmentBoundaryNote> byRental = new LinkedHashMap<>();
                Map<Long, SubEquipmentBoundaryNote> byHold = new LinkedHashMap<>();
                for (SubEquipmentBoundaryNote n : notes) {
                        if (n.rentalId() != null) {
                                byRental.merge(n.rentalId(), n, (a, b) -> new SubEquipmentBoundaryNote(
                                                a.rentalId(), null,
                                                a.availableAfter() != null ? a.availableAfter() : b.availableAfter(),
                                                a.mustReturnBefore() != null ? a.mustReturnBefore()
                                                                : b.mustReturnBefore(),
                                                Math.max(a.quantity(), b.quantity())));
                        } else if (n.holdId() != null) {
                                byHold.merge(n.holdId(), n, (a, b) -> new SubEquipmentBoundaryNote(
                                                null, a.holdId(),
                                                a.availableAfter() != null ? a.availableAfter() : b.availableAfter(),
                                                a.mustReturnBefore() != null ? a.mustReturnBefore()
                                                                : b.mustReturnBefore(),
                                                Math.max(a.quantity(), b.quantity())));
                        }
                }
                List<SubEquipmentBoundaryNote> result = new ArrayList<>();
                result.addAll(byRental.values());
                result.addAll(byHold.values());
                return result;
        }

        private RentalCategory resolvePricingCategory(Long pricingCategoryId) {
                if (pricingCategoryId == null)
                        return null;
                return rentalCategoryRepository.findById(pricingCategoryId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Pricing category not found with id: " + pricingCategoryId));
        }

        private void checkEquipmentBookingConflicts(Long equipmentId,
                        LocalDateTime effectiveStart, LocalDateTime effectiveEnd) {
                if (equipmentRentalItemRepository.existsConflictingApprovedRental(
                                equipmentId, effectiveStart, effectiveEnd, 0L, RENTAL_BLOCKING)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Equipment has an active rental booking in the requested datetime range");
                }
                if (eventEquipmentRequestItemRepository.existsConflictingRequest(
                                equipmentId, 0L, List.copyOf(EVENT_BLOCKING), effectiveStart, effectiveEnd)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Equipment has an active event request in the requested datetime range");
                }
        }

        private SubQuantityMaps buildSubQuantityMaps(List<Long> subIds,
                        LocalDateTime start, LocalDateTime end) {
                return buildSubQuantityMaps(subIds, start, end, 0L);
        }

        private SubQuantityMaps buildSubQuantityMaps(List<Long> subIds,
                        LocalDateTime start, LocalDateTime end, Long excludeRentalId) {
                if (subIds.isEmpty())
                        return new SubQuantityMaps(Map.of(), Map.of(), Map.of());
                Map<Long, Integer> rental = equipmentRentalSubItemRepository
                                .sumCommittedQuantityPerSubEquipment(start, end, RENTAL_BLOCKING, excludeRentalId)
                                .stream().collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));
                Map<Long, Integer> event = eventEquipmentRequestSubItemRepository
                                .sumCommittedQuantityPerSubEquipment(start, end, EVENT_BLOCKING)
                                .stream().collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));
                Map<Long, Integer> hold = subEquipmentQuantityHoldRepository
                                .sumHeldQuantityPerSubEquipment(start, end)
                                .stream().collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));
                return new SubQuantityMaps(rental, event, hold);
        }

        private Map<Long, List<SubEquipmentBoundaryNote>> buildSubBoundaryNotes(
                        List<Long> subIds, LocalDate startDate, LocalDate endDate, Long excludeRentalId) {
                Map<Long, List<SubEquipmentBoundaryNote>> map = new HashMap<>();
                if (subIds.isEmpty())
                        return map;
                LocalDateTime startBoundary = startDate.atStartOfDay();
                LocalDateTime endBoundary = endDate.atStartOfDay();
                equipmentRentalSubItemRepository
                                .findBoundaryReturnQuantities(subIds, startBoundary, RENTAL_BLOCKING, excludeRentalId)
                                .forEach(row -> {
                                        Long sid = (Long) row[0];
                                        Long rentalId = (Long) row[1];
                                        LocalDateTime time = ((LocalDateTime) row[2]).plusMinutes(bufferMinutes);
                                        int qty = ((Number) row[3]).intValue();
                                        map.computeIfAbsent(sid, k -> new ArrayList<>())
                                                        .add(new SubEquipmentBoundaryNote(rentalId, null, time, null,
                                                                        qty));
                                });
                equipmentRentalSubItemRepository
                                .findBoundaryPickupQuantities(subIds, endBoundary, RENTAL_BLOCKING, excludeRentalId)
                                .forEach(row -> {
                                        Long sid = (Long) row[0];
                                        Long rentalId = (Long) row[1];
                                        LocalDateTime time = ((LocalDateTime) row[2]).minusMinutes(bufferMinutes);
                                        int qty = ((Number) row[3]).intValue();
                                        map.computeIfAbsent(sid, k -> new ArrayList<>())
                                                        .add(new SubEquipmentBoundaryNote(rentalId, null, null, time,
                                                                        qty));
                                });
                subEquipmentQuantityHoldRepository
                                .findBoundaryEndQuantities(subIds, startBoundary)
                                .forEach(row -> {
                                        Long sid = (Long) row[0];
                                        Long holdId = (Long) row[1];
                                        LocalDateTime time = ((LocalDateTime) row[2]).plusMinutes(bufferMinutes);
                                        int qty = ((Number) row[3]).intValue();
                                        map.computeIfAbsent(sid, k -> new ArrayList<>())
                                                        .add(new SubEquipmentBoundaryNote(null, holdId, time, null,
                                                                        qty));
                                });
                subEquipmentQuantityHoldRepository
                                .findBoundaryStartQuantities(subIds, endBoundary)
                                .forEach(row -> {
                                        Long sid = (Long) row[0];
                                        Long holdId = (Long) row[1];
                                        LocalDateTime time = ((LocalDateTime) row[2]).minusMinutes(bufferMinutes);
                                        int qty = ((Number) row[3]).intValue();
                                        map.computeIfAbsent(sid, k -> new ArrayList<>())
                                                        .add(new SubEquipmentBoundaryNote(null, holdId, null, time,
                                                                        qty));
                                });
                eventEquipmentRequestSubItemRepository
                                .findBoundaryReturnQuantities(subIds, startBoundary, EVENT_BLOCKING)
                                .forEach(row -> {
                                        Long sid = (Long) row[0];
                                        Long eventRequestId = (Long) row[1];
                                        LocalDateTime time = ((LocalDateTime) row[2]).plusMinutes(bufferMinutes);
                                        int qty = ((Number) row[3]).intValue();
                                        map.computeIfAbsent(sid, k -> new ArrayList<>())
                                                        .add(new SubEquipmentBoundaryNote(eventRequestId, null, time,
                                                                        null, qty));
                                });
                eventEquipmentRequestSubItemRepository
                                .findBoundaryPickupQuantities(subIds, endBoundary, EVENT_BLOCKING)
                                .forEach(row -> {
                                        Long sid = (Long) row[0];
                                        Long eventRequestId = (Long) row[1];
                                        LocalDateTime time = ((LocalDateTime) row[2]).minusMinutes(bufferMinutes);
                                        int qty = ((Number) row[3]).intValue();
                                        map.computeIfAbsent(sid, k -> new ArrayList<>())
                                                        .add(new SubEquipmentBoundaryNote(eventRequestId, null, null,
                                                                        time, qty));
                                });
                return map;
        }

        private PartialCandidateResult analyzePartialCandidates(List<Long> partialCandidateIds,
                        LocalDate startDate, LocalDate endDate,
                        LocalDateTime effectiveStart, LocalDateTime effectiveEnd, Long excludeRentalId) {
                Map<Long, MainEquipmentStatusType> statusMap = new HashMap<>();
                Map<Long, List<EquipmentBoundaryNote>> boundaryMap = new HashMap<>();

                List<Object[]> rentalConflicts = equipmentRentalItemRepository
                                .findRentalConflictsForEquipment(partialCandidateIds, startDate, endDate,
                                                RENTAL_BLOCKING, excludeRentalId);
                List<Object[]> statusConflicts = mainEquipmentStatusRepository
                                .findStatusWindowConflictsForEquipment(partialCandidateIds, effectiveStart,
                                                effectiveEnd);
                List<Object[]> eventConflicts = eventEquipmentRequestItemRepository
                                .findEventConflictsForEquipment(partialCandidateIds, startDate, endDate,
                                                EVENT_BLOCKING);

                Map<Long, List<Object[]>> rentalByEq = rentalConflicts.stream()
                                .collect(Collectors.groupingBy(row -> (Long) row[0]));
                Map<Long, List<Object[]>> statusByEq = statusConflicts.stream()
                                .collect(Collectors.groupingBy(row -> (Long) row[0]));
                Map<Long, List<Object[]>> eventByEq = eventConflicts.stream()
                                .collect(Collectors.groupingBy(row -> (Long) row[0]));

                for (Long equipmentId : partialCandidateIds) {
                        List<Object[]> rConflicts = rentalByEq.getOrDefault(equipmentId, List.of());
                        List<Object[]> sConflicts = statusByEq.getOrDefault(equipmentId, List.of());
                        List<Object[]> eConflicts = eventByEq.getOrDefault(equipmentId, List.of());

                        boolean hasInteriorConflict = false;
                        List<EquipmentBoundaryNote> notes = new ArrayList<>();

                        for (Object[] row : rConflicts) {
                                Long rentalId = (Long) row[1];
                                LocalDateTime returnDt = (LocalDateTime) row[2];
                                LocalDateTime pickupDt = (LocalDateTime) row[3];
                                LocalDate progStart = (LocalDate) row[4];
                                LocalDate progEnd = (LocalDate) row[5];

                                LocalDate effEnd = returnDt != null ? returnDt.toLocalDate() : progEnd;
                                LocalDate effStart = pickupDt != null ? pickupDt.toLocalDate() : progStart;

                                boolean isStartBoundary = effEnd.equals(startDate);
                                boolean isEndBoundary = effStart.equals(endDate);

                                if (!isStartBoundary && !isEndBoundary) {
                                        hasInteriorConflict = true;
                                        break;
                                }
                                if (isStartBoundary && returnDt != null)
                                        notes.add(new EquipmentBoundaryNote(rentalId, null, null,
                                                        returnDt.plusMinutes(bufferMinutes), null));
                                if (isEndBoundary && pickupDt != null)
                                        notes.add(new EquipmentBoundaryNote(rentalId, null, null, null,
                                                        pickupDt.minusMinutes(bufferMinutes)));
                        }

                        if (!hasInteriorConflict) {
                                for (Object[] row : sConflicts) {
                                        Long statusId = (Long) row[1];
                                        LocalDateTime sStart = (LocalDateTime) row[2];
                                        LocalDateTime sEnd = (LocalDateTime) row[3];

                                        boolean isStartBoundary = sEnd.toLocalDate().equals(startDate);
                                        boolean isEndBoundary = sStart.toLocalDate().equals(endDate);

                                        if (!isStartBoundary && !isEndBoundary) {
                                                hasInteriorConflict = true;
                                                break;
                                        }
                                        if (isStartBoundary)
                                                notes.add(new EquipmentBoundaryNote(null, statusId, null,
                                                                sEnd.plusMinutes(bufferMinutes), null));
                                        if (isEndBoundary)
                                                notes.add(new EquipmentBoundaryNote(null, statusId, null, null,
                                                                sStart.minusMinutes(bufferMinutes)));
                                }
                        }

                        if (!hasInteriorConflict) {
                                for (Object[] row : eConflicts) {
                                        Long eventRequestId = (Long) row[1];
                                        LocalDateTime effEndDt = (LocalDateTime) row[2];
                                        LocalDateTime effStartDt = (LocalDateTime) row[3];

                                        LocalDate effEnd = effEndDt.toLocalDate();
                                        LocalDate effStart = effStartDt.toLocalDate();

                                        boolean isStartBoundary = effEnd.equals(startDate);
                                        boolean isEndBoundary = effStart.equals(endDate);

                                        if (!isStartBoundary && !isEndBoundary) {
                                                hasInteriorConflict = true;
                                                break;
                                        }
                                        if (isStartBoundary)
                                                notes.add(new EquipmentBoundaryNote(null, null, eventRequestId,
                                                                effEndDt.plusMinutes(bufferMinutes), null));
                                        if (isEndBoundary)
                                                notes.add(new EquipmentBoundaryNote(null, null, eventRequestId, null,
                                                                effStartDt.minusMinutes(bufferMinutes)));
                                }
                        }

                        boolean hasImpossibleWindow = notes.stream().anyMatch(n -> (n.availableAfter() != null
                                        && n.availableAfter().toLocalDate().isAfter(startDate)) ||
                                        (n.mustReturnBefore() != null
                                                        && n.mustReturnBefore().toLocalDate()
                                                                        .isBefore(endDate)));

                        if (hasInteriorConflict || notes.isEmpty() || hasImpossibleWindow) {
                                statusMap.put(equipmentId, MainEquipmentStatusType.BOOKED);
                        } else {
                                boolean hasAfter = notes.stream().anyMatch(n -> n.availableAfter() != null);
                                boolean hasBefore = notes.stream().anyMatch(n -> n.mustReturnBefore() != null);
                                if (startDate.equals(endDate) && hasAfter && hasBefore) {
                                        statusMap.put(equipmentId, MainEquipmentStatusType.BOOKED);
                                } else {
                                        statusMap.put(equipmentId, MainEquipmentStatusType.PARTIALLY_AVAILABLE);
                                        boundaryMap.put(equipmentId, mergeBoundaryNotes(notes));
                                }
                        }
                }
                return new PartialCandidateResult(statusMap, boundaryMap);
        }

        private record SubQuantityMaps(
                        Map<Long, Integer> rental,
                        Map<Long, Integer> event,
                        Map<Long, Integer> hold) {
        }

        private record PartialCandidateResult(
                        Map<Long, MainEquipmentStatusType> statusMap,
                        Map<Long, List<EquipmentBoundaryNote>> boundaryMap) {
        }
}
