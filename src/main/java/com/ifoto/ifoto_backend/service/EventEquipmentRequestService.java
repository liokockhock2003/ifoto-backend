package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO.EquipmentRequestSubItemRequest;
import com.ifoto.ifoto_backend.model.*;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventEquipmentRequestService {

    private final EventEquipmentRequestRepository requestRepository;
    private final EventEquipmentRequestItemRepository requestItemRepository;
    private final EventEquipmentRequestSubItemRepository subItemRepository;
    private final EventRepository eventRepository;
    private final MainEquipmentRepository mainEquipmentRepository;
    private final SubEquipmentRepository subEquipmentRepository;
    private final UserRepository userRepository;
    private final MainEquipmentStatusRepository mainEquipmentStatusRepository;
    private final SubEquipmentQuantityHoldRepository subEquipmentQuantityHoldRepository;
    private final EquipmentRentalItemRepository rentalItemRepository;

    @Value("${app.rental.buffer-minutes:60}")
    private int bufferMinutes;

    private static final List<EventEquipmentRequestStatus> BLOCKING_STATUSES =
            List.of(EventEquipmentRequestStatus.APPROVED, EventEquipmentRequestStatus.ACTIVE);

    private static final List<RentalStatus> RENTAL_BLOCKING_STATUSES = List.of(
            RentalStatus.APPROVED, RentalStatus.PICKED_UP, RentalStatus.PENDING_PAYMENT,
            RentalStatus.PAID, RentalStatus.ACTIVE, RentalStatus.OVERDUE);

    // ── Submit ────────────────────────────────────────────────────────────────

    @Transactional
    public EventEquipmentRequest submitRequest(Long eventId, List<Long> equipmentIds,
            String notes, List<EquipmentRequestSubItemRequest> subEquipmentEntries, String username) {
        User requester = findUser(username);
        Event event = findEvent(eventId);

        boolean isMember = event.getEventCommittee().stream()
                .anyMatch(u -> u.getId().equals(requester.getId()));
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not a committee member of this event");
        }

        // Auto-set datetime from event
        LocalDateTime startDatetime = event.getStartDatetime();
        LocalDateTime endDatetime   = event.getEndDatetime();

        List<MainEquipment> equipmentList = resolveAndValidateEquipment(equipmentIds);

        LocalDateTime effectiveStart = startDatetime.minusMinutes(bufferMinutes);
        LocalDateTime effectiveEnd   = endDatetime.plusMinutes(bufferMinutes);
        checkRequestConflicts(equipmentList, effectiveStart, effectiveEnd, 0L);
        checkStatusConflicts(equipmentList, effectiveStart, effectiveEnd);

        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .event(event)
                .requestedBy(requester)
                .status(EventEquipmentRequestStatus.PENDING_REVIEW)
                .startDatetime(startDatetime)
                .endDatetime(endDatetime)
                .requesterNotes(notes)
                .build();
        request = requestRepository.save(request);

        int year = LocalDateTime.now().getYear();
        request.setRequestNumber("EER-%d-%06d".formatted(year, request.getId()));

        List<EventEquipmentRequestItem> items = buildItems(request, equipmentList);
        request.getItems().addAll(items);
        request = requestRepository.save(request);

        buildAndSaveSubItems(request, subEquipmentEntries, startDatetime, endDatetime, 0L);
        return request;
    }

    // ── Review (approve / reject) ─────────────────────────────────────────────

    @Transactional
    public EventEquipmentRequest reviewRequest(Long id, String action,
            List<Long> equipmentIds,
            List<EquipmentRequestSubItemRequest> subEquipmentEntries,
            String rejectionReason, String committeeNotes,
            LocalDateTime pickupDatetime, LocalDateTime returnDatetime, String username) {
        User committee = findUser(username);
        EventEquipmentRequest request = findRequest(id);

        if (request.getStatus() != EventEquipmentRequestStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Request is not in PENDING_REVIEW status");
        }

        if ("REJECT".equalsIgnoreCase(action)) {
            request.setStatus(EventEquipmentRequestStatus.REJECTED);
            request.setReviewedBy(committee);
            request.setRejectionReason(rejectionReason);
            request.setCommitteeNotes(committeeNotes);
            return requestRepository.save(request);
        }

        if (!"APPROVE".equalsIgnoreCase(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action must be APPROVE or REJECT");
        }

        Event event = request.getEvent();
        LocalDateTime pickupDt = pickupDatetime != null ? pickupDatetime : event.getStartDatetime();
        LocalDateTime returnDt = returnDatetime  != null ? returnDatetime  : event.getEndDatetime();

        List<Long> finalEquipmentIds = (equipmentIds != null && !equipmentIds.isEmpty())
                ? equipmentIds
                : request.getItems().stream()
                        .map(i -> i.getMainEquipment().getMainEquipmentId()).toList();
        List<MainEquipment> equipmentList = resolveAndValidateEquipment(finalEquipmentIds);

        LocalDateTime effectiveStart = pickupDt.minusMinutes(bufferMinutes);
        LocalDateTime effectiveEnd   = returnDt.plusMinutes(bufferMinutes);
        checkRequestConflicts(equipmentList, effectiveStart, effectiveEnd, id);
        checkStatusConflicts(equipmentList, effectiveStart, effectiveEnd);

        int durationDays = (int) ChronoUnit.DAYS.between(
                pickupDt.toLocalDate(), returnDt.toLocalDate()) + 1;

        request.getItems().clear();
        List<EventEquipmentRequestItem> items = buildItems(request, equipmentList);
        request.getItems().addAll(items);

        request.setPickupDatetime(pickupDt);
        request.setReturnDatetime(returnDt);
        request.setDurationDays(durationDays);
        request.setStatus(EventEquipmentRequestStatus.APPROVED);
        request.setReviewedBy(committee);
        request.setCommitteeNotes(committeeNotes);
        request.setApprovedAt(LocalDateTime.now());

        request = requestRepository.save(request);

        if (subEquipmentEntries != null) {
            request.getSubItems().clear();
            requestRepository.save(request);
            buildAndSaveSubItems(request, subEquipmentEntries,
                    request.getStartDatetime(), request.getEndDatetime(), id);
        }

        return request;
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Transactional
    public EventEquipmentRequest cancelRequest(Long id, String username) {
        EventEquipmentRequest request = findRequest(id);
        User requester = findUser(username);

        if (!request.getRequestedBy().getId().equals(requester.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        if (request.getStatus() != EventEquipmentRequestStatus.PENDING_REVIEW
                && request.getStatus() != EventEquipmentRequestStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PENDING_REVIEW or APPROVED requests can be cancelled");
        }
        request.setStatus(EventEquipmentRequestStatus.CANCELLED);
        return requestRepository.save(request);
    }

    // ── Committee lifecycle ────────────────────────────────────────────────────

    @Transactional
    public EventEquipmentRequest markPickedUp(Long id, String username) {
        EventEquipmentRequest request = findRequest(id);
        if (request.getStatus() != EventEquipmentRequestStatus.APPROVED
                && request.getStatus() != EventEquipmentRequestStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Request must be APPROVED or ACTIVE to mark as picked up");
        }
        requireApproverOrFallback(request, findUser(username));
        request.setPickedUpAt(LocalDateTime.now());
        return requestRepository.save(request);
    }

    @Transactional
    public EventEquipmentRequest markActive(Long id) {
        EventEquipmentRequest request = findRequest(id);
        if (request.getStatus() != EventEquipmentRequestStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Request must be APPROVED before marking active");
        }
        request.setStatus(EventEquipmentRequestStatus.ACTIVE);
        request.setActiveAt(LocalDateTime.now());
        return requestRepository.save(request);
    }

    @Transactional
    public EventEquipmentRequest markReturned(Long id) {
        EventEquipmentRequest request = findRequest(id);
        if (request.getStatus() != EventEquipmentRequestStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Request must be ACTIVE to mark returned");
        }
        request.setStatus(EventEquipmentRequestStatus.RETURNED);
        request.setReturnedAt(LocalDateTime.now());
        return requestRepository.save(request);
    }

    // ── Logistics update (pickup/return datetime) ─────────────────────────────

    @Transactional
    public EventEquipmentRequest updateLogistics(Long id, String username,
            LocalDateTime pickupDatetime, LocalDateTime returnDatetime) {
        EventEquipmentRequest request = findRequest(id);
        if (request.getStatus() != EventEquipmentRequestStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Logistics can only be updated when request is APPROVED");
        }
        requireApproverOrFallback(request, findUser(username));
        validateDatetimeBounds(pickupDatetime, returnDatetime);

        List<MainEquipment> equipmentList = request.getItems().stream()
                .map(EventEquipmentRequestItem::getMainEquipment).toList();
        LocalDateTime effectiveStart = pickupDatetime.minusMinutes(bufferMinutes);
        LocalDateTime effectiveEnd   = returnDatetime.plusMinutes(bufferMinutes);
        checkRequestConflicts(equipmentList, effectiveStart, effectiveEnd, id);
        checkStatusConflicts(equipmentList, effectiveStart, effectiveEnd);

        request.setPickupDatetime(pickupDatetime);
        request.setReturnDatetime(returnDatetime);
        request.setDurationDays((int) ChronoUnit.DAYS.between(
                pickupDatetime.toLocalDate(), returnDatetime.toLocalDate()) + 1);
        return requestRepository.save(request);
    }

    // ── Equipment update (swap items) ─────────────────────────────────────────

    @Transactional
    public EventEquipmentRequest updateEquipment(Long id, String username,
            List<Long> equipmentIds, List<EquipmentRequestSubItemRequest> subEquipmentEntries) {
        EventEquipmentRequest request = findRequest(id);
        if (request.getStatus() != EventEquipmentRequestStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Equipment can only be updated when request is APPROVED");
        }
        requireApproverOrFallback(request, findUser(username));

        List<MainEquipment> equipmentList = resolveAndValidateEquipment(equipmentIds);
        LocalDateTime baseStart = request.getPickupDatetime() != null
                ? request.getPickupDatetime() : request.getStartDatetime();
        LocalDateTime baseEnd = request.getReturnDatetime() != null
                ? request.getReturnDatetime() : request.getEndDatetime();
        LocalDateTime effectiveStart = baseStart.minusMinutes(bufferMinutes);
        LocalDateTime effectiveEnd   = baseEnd.plusMinutes(bufferMinutes);
        checkRequestConflicts(equipmentList, effectiveStart, effectiveEnd, id);
        checkStatusConflicts(equipmentList, effectiveStart, effectiveEnd);

        request.getItems().clear();
        request.getItems().addAll(buildItems(request, equipmentList));

        if (subEquipmentEntries != null) {
            request.getSubItems().clear();
            requestRepository.save(request);
            buildAndSaveSubItems(request, subEquipmentEntries,
                    request.getStartDatetime(), request.getEndDatetime(), id);
        }
        return requestRepository.save(request);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EventEquipmentRequest> getMyRequests(String username) {
        User requester = findUser(username);
        return requestRepository.findByRequestedByOrderByCreatedAtDesc(requester);
    }

    @Transactional(readOnly = true)
    public List<EventEquipmentRequest> getRequestsByEvent(Long eventId, String username) {
        User requester = findUser(username);
        Event event = findEvent(eventId);

        boolean isMember = event.getEventCommittee().stream()
                .anyMatch(u -> u.getId().equals(requester.getId()));
        boolean isEquipmentCommittee = requester.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_EQUIPMENT_COMMITTEE"));
        if (!isMember && !isEquipmentCommittee) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not a committee member of this event");
        }
        return requestRepository.findByEventOrderByCreatedAtDesc(event);
    }

    @Transactional(readOnly = true)
    public List<EventEquipmentRequest> getRequestsByEquipment(Long equipmentId) {
        return requestRepository.findByEquipmentId(equipmentId);
    }

    @Transactional(readOnly = true)
    public List<EventEquipmentRequest> getRequestsBySubEquipment(Long subEquipmentId) {
        return requestRepository.findBySubEquipmentId(subEquipmentId);
    }

    @Transactional(readOnly = true)
    public Page<EventEquipmentRequest> getAllRequests(String search, String status, int page, int size) {
        int clampedPage = Math.max(page, 0);
        int clampedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);
        String s = (search == null) ? "" : search.trim();
        EventEquipmentRequestStatus st = null;
        if (status != null && !status.isBlank()) {
            try {
                st = EventEquipmentRequestStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        return requestRepository.searchRequests(s, st, pageable);
    }

    // ── Equipment schedules ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public com.ifoto.ifoto_backend.dto.EquipmentDTO.EquipmentSchedulesResponse getEquipmentSchedules(
            Long requestId,
            List<Long> mainEquipmentIds,
            List<Long> subEquipmentIds,
            String username) {
        EventEquipmentRequest request = findRequest(requestId);
        User caller = findUser(username);

        boolean isCommittee = caller.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_EQUIPMENT_COMMITTEE")
                        || r.getName().equals("ROLE_EVENT_COMMITTEE")
                        || r.getName().equals("ROLE_HIGH_COMMITTEE"));
        boolean isOwner = request.getRequestedBy().getId().equals(caller.getId());
        if (!isCommittee && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        List<Long> mainIds = (mainEquipmentIds != null && !mainEquipmentIds.isEmpty())
                ? mainEquipmentIds
                : request.getItems().stream()
                        .map(i -> i.getMainEquipment().getMainEquipmentId()).toList();
        List<Long> subIds = (subEquipmentIds != null && !subEquipmentIds.isEmpty())
                ? subEquipmentIds
                : request.getSubItems().stream()
                        .map(s -> s.getSubEquipment().getSubEquipmentId()).toList();

        java.util.Map<Long, List<com.ifoto.ifoto_backend.dto.EquipmentDTO.MainEquipmentStatusResponse>> statusMap =
                mainIds.isEmpty() ? java.util.Map.of()
                        : mainEquipmentStatusRepository.findAllByEquipmentIds(mainIds)
                                .stream()
                                .collect(java.util.stream.Collectors.groupingBy(
                                        s -> s.getMainEquipment().getMainEquipmentId(),
                                        java.util.stream.Collectors.mapping(
                                                s -> new com.ifoto.ifoto_backend.dto.EquipmentDTO.MainEquipmentStatusResponse(
                                                        s.getId(), s.getStatusType(),
                                                        s.getStartDatetime(), s.getEndDatetime(), s.getNotes()),
                                                java.util.stream.Collectors.toList())));

        java.util.Map<Long, List<com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentQuantityHoldResponse>> holdMap =
                subIds.isEmpty() ? java.util.Map.of()
                        : subEquipmentQuantityHoldRepository.findAllBySubEquipmentIds(subIds)
                                .stream()
                                .collect(java.util.stream.Collectors.groupingBy(
                                        h -> h.getSubEquipment().getSubEquipmentId(),
                                        java.util.stream.Collectors.mapping(
                                                h -> new com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentQuantityHoldResponse(
                                                        h.getId(), h.getQuantity(),
                                                        h.getStartDatetime(), h.getEndDatetime(), h.getNotes()),
                                                java.util.stream.Collectors.toList())));

        List<com.ifoto.ifoto_backend.dto.EquipmentDTO.MainEquipmentScheduleEntry> mainEntries =
                mainIds.stream()
                        .map(id -> {
                            MainEquipment eq = mainEquipmentRepository.findById(id)
                                    .orElseThrow(() -> new ResponseStatusException(
                                            HttpStatus.NOT_FOUND, "Equipment not found: " + id));
                            return new com.ifoto.ifoto_backend.dto.EquipmentDTO.MainEquipmentScheduleEntry(
                                    eq.getMainEquipmentId(), eq.getBrand(), eq.getModel(),
                                    eq.getSerialNumber(),
                                    statusMap.getOrDefault(id, List.of()));
                        }).toList();

        List<com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentScheduleEntry> subEntries =
                subIds.stream()
                        .map(id -> {
                            SubEquipment sub = subEquipmentRepository.findById(id)
                                    .orElseThrow(() -> new ResponseStatusException(
                                            HttpStatus.NOT_FOUND, "Sub-equipment not found: " + id));
                            int qty = request.getSubItems().stream()
                                    .filter(s -> s.getSubEquipment().getSubEquipmentId().equals(id))
                                    .mapToInt(EventEquipmentRequestSubItem::getBorrowedQuantity).sum();
                            return new com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentScheduleEntry(
                                    sub.getSubEquipmentId(), sub.getType(), sub.getBrand(),
                                    sub.getCameraModel(), qty,
                                    holdMap.getOrDefault(id, List.<com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentQuantityHoldResponse>of()));
                        }).toList();

        return new com.ifoto.ifoto_backend.dto.EquipmentDTO.EquipmentSchedulesResponse(mainEntries, subEntries);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void checkRequestConflicts(List<MainEquipment> equipmentList,
            LocalDateTime effectiveStart, LocalDateTime effectiveEnd, Long excludeRequestId) {
        List<String> eventConflicts = equipmentList.stream()
                .filter(eq -> requestItemRepository.existsConflictingRequest(
                        eq.getMainEquipmentId(), excludeRequestId, BLOCKING_STATUSES,
                        effectiveStart, effectiveEnd))
                .map(eq -> eq.getBrand() + " " + eq.getModel()
                        + (eq.getSerialNumber() != null ? " (" + eq.getSerialNumber() + ")" : ""))
                .toList();
        if (!eventConflicts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The following equipment conflicts with an existing approved event request: "
                            + String.join(", ", eventConflicts));
        }
        List<String> rentalConflicts = equipmentList.stream()
                .filter(eq -> rentalItemRepository.existsConflictingApprovedRental(
                        eq.getMainEquipmentId(), effectiveStart, effectiveEnd, 0L, RENTAL_BLOCKING_STATUSES))
                .map(eq -> eq.getBrand() + " " + eq.getModel()
                        + (eq.getSerialNumber() != null ? " (" + eq.getSerialNumber() + ")" : ""))
                .toList();
        if (!rentalConflicts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The following equipment conflicts with an existing approved rental: "
                            + String.join(", ", rentalConflicts));
        }
    }

    private void checkStatusConflicts(List<MainEquipment> equipmentList,
            LocalDateTime effectiveStart, LocalDateTime effectiveEnd) {
        List<String> conflicts = equipmentList.stream()
                .filter(eq -> mainEquipmentStatusRepository.existsConflictingStatus(
                        eq.getMainEquipmentId(), effectiveStart, effectiveEnd))
                .map(eq -> eq.getBrand() + " " + eq.getModel()
                        + (eq.getSerialNumber() != null ? " (" + eq.getSerialNumber() + ")" : ""))
                .toList();
        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The following equipment is under Kelab Fotokreatif's usage for the requested period: "
                            + String.join(", ", conflicts));
        }
    }

    private void requireApproverOrFallback(EventEquipmentRequest request, User requester) {
        User reviewer = request.getReviewedBy();
        boolean isOriginalApprover = reviewer != null && reviewer.getId().equals(requester.getId());
        boolean approverStillActive = reviewer != null && reviewer.isActive()
                && reviewer.getRoles().stream()
                        .anyMatch(r -> r.getName().equals("ROLE_EQUIPMENT_COMMITTEE"));
        if (!isOriginalApprover && approverStillActive) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the approving Equipment Committee member may perform this action");
        }
    }

    private void validateDatetimeBounds(LocalDateTime startDatetime, LocalDateTime endDatetime) {
        if (!startDatetime.isBefore(endDatetime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Start datetime must be before end datetime");
        }
    }

    private void buildAndSaveSubItems(EventEquipmentRequest request,
            List<EquipmentRequestSubItemRequest> entries,
            LocalDateTime startDatetime, LocalDateTime endDatetime, Long excludeRequestId) {
        if (entries == null || entries.isEmpty()) return;
        LocalDateTime effectiveStart = startDatetime.minusMinutes(bufferMinutes);
        LocalDateTime effectiveEnd   = endDatetime.plusMinutes(bufferMinutes);
        List<EventEquipmentRequestSubItem> subItems = new ArrayList<>();
        for (EquipmentRequestSubItemRequest entry : entries) {
            SubEquipment sub = subEquipmentRepository.findById(entry.subEquipmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Sub-equipment not found: " + entry.subEquipmentId()));
            int committed = subItemRepository.sumCommittedQuantity(
                    sub.getSubEquipmentId(), effectiveStart, effectiveEnd, BLOCKING_STATUSES, excludeRequestId);
            int held = subEquipmentQuantityHoldRepository.sumHeldQuantity(
                    sub.getSubEquipmentId(), startDatetime, endDatetime, 0L);
            if (committed + held + entry.quantity() > sub.getTotalQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient quantity for '" + sub.getType() + " " + sub.getBrand()
                                + "': requested " + entry.quantity()
                                + ", available " + (sub.getTotalQuantity() - committed - held));
            }
            subItems.add(EventEquipmentRequestSubItem.builder()
                    .eventEquipmentRequest(request)
                    .subEquipment(sub)
                    .borrowedQuantity(entry.quantity())
                    .build());
        }
        subItemRepository.saveAll(subItems);
        request.getSubItems().addAll(subItems);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found: " + username));
    }

    private Event findEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Event not found: " + id));
    }

    private EventEquipmentRequest findRequest(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Equipment request not found: " + id));
    }

    private List<MainEquipment> resolveAndValidateEquipment(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one equipment item is required");
        }
        List<MainEquipment> list = new ArrayList<>();
        for (Long eqId : ids) {
            MainEquipment eq = mainEquipmentRepository.findById(eqId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Equipment not found: " + eqId));
            list.add(eq);
        }
        return list;
    }

    private List<EventEquipmentRequestItem> buildItems(EventEquipmentRequest request,
            List<MainEquipment> equipmentList) {
        List<EventEquipmentRequestItem> items = new ArrayList<>();
        for (MainEquipment eq : equipmentList) {
            items.add(EventEquipmentRequestItem.builder()
                    .eventEquipmentRequest(request)
                    .mainEquipment(eq)
                    .build());
        }
        return requestItemRepository.saveAll(items);
    }
}
