package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO.EquipmentRequestSubItemRequest;
import com.ifoto.ifoto_backend.model.*;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import com.ifoto.ifoto_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private static final List<EventEquipmentRequestStatus> BLOCKING_STATUSES =
            List.of(EventEquipmentRequestStatus.APPROVED, EventEquipmentRequestStatus.ACTIVE);

    // ── Submit ────────────────────────────────────────────────────────────────

    @Transactional
    public EventEquipmentRequest submitRequest(Long eventId, List<Long> equipmentIds,
            LocalDate startDate, LocalDate endDate, String notes,
            List<EquipmentRequestSubItemRequest> subEquipmentEntries, String username) {
        User requester = findUser(username);
        Event event = findEvent(eventId);

        boolean isMember = event.getEventCommittee().stream()
                .anyMatch(u -> u.getId().equals(requester.getId()));
        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not a committee member of this event");
        }

        List<MainEquipment> equipmentList = resolveAndValidateEquipment(equipmentIds);

        for (MainEquipment eq : equipmentList) {
            if (requestItemRepository.existsConflictingRequest(
                    eq.getMainEquipmentId(), 0L, BLOCKING_STATUSES, startDate, endDate)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Equipment '" + eq.getBrand() + " " + eq.getModel() + " (" + eq.getSerialNumber()
                                + ")' is already requested for the given dates");
            }
            if (mainEquipmentStatusRepository.existsConflictingStatus(
                    eq.getMainEquipmentId(), startDate, endDate)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Equipment '" + eq.getSerialNumber()
                                + "' is under an admin hold for the requested dates");
            }
        }

        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .event(event)
                .requestedBy(requester)
                .status(EventEquipmentRequestStatus.PENDING_REVIEW)
                .requestedStartDate(startDate)
                .requestedEndDate(endDate)
                .requesterNotes(notes)
                .build();
        request = requestRepository.save(request);

        int year = LocalDateTime.now().getYear();
        request.setRequestNumber("EER-%d-%06d".formatted(year, request.getId()));

        List<EventEquipmentRequestItem> items = buildItems(request, equipmentList);
        request.getItems().addAll(items);
        request = requestRepository.save(request);

        buildAndSaveSubItems(request, subEquipmentEntries, startDate, endDate, 0L);
        return request;
    }

    // ── Review (approve / reject) ─────────────────────────────────────────────

    @Transactional
    public EventEquipmentRequest reviewRequest(Long id, String action, LocalDate approvedStart,
            LocalDate approvedEnd, List<Long> equipmentIds,
            List<EquipmentRequestSubItemRequest> subEquipmentEntries,
            String rejectionReason, String committeeNotes, String username) {
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

        if (approvedStart == null || approvedEnd == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "approvedStartDate and approvedEndDate are required for approval");
        }
        if (!approvedEnd.isAfter(approvedStart) && !approvedEnd.isEqual(approvedStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "approvedEndDate must be on or after approvedStartDate");
        }

        List<Long> finalEquipmentIds = (equipmentIds != null && !equipmentIds.isEmpty())
                ? equipmentIds
                : request.getItems().stream()
                        .map(i -> i.getMainEquipment().getMainEquipmentId()).toList();
        List<MainEquipment> equipmentList = resolveAndValidateEquipment(finalEquipmentIds);

        for (MainEquipment eq : equipmentList) {
            if (requestItemRepository.existsConflictingRequest(
                    eq.getMainEquipmentId(), id, BLOCKING_STATUSES, approvedStart, approvedEnd)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Equipment '" + eq.getBrand() + " " + eq.getModel() + " (" + eq.getSerialNumber()
                                + ")' conflicts with an existing approved request");
            }
            if (mainEquipmentStatusRepository.existsConflictingStatus(
                    eq.getMainEquipmentId(), approvedStart, approvedEnd)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Equipment '" + eq.getSerialNumber()
                                + "' is under an admin hold for the approved dates");
            }
        }

        int durationDays = (int) (approvedEnd.toEpochDay() - approvedStart.toEpochDay() + 1);

        request.getItems().clear();
        List<EventEquipmentRequestItem> items = buildItems(request, equipmentList);
        request.getItems().addAll(items);

        request.setDurationDays(durationDays);
        request.setApprovedStartDate(approvedStart);
        request.setApprovedEndDate(approvedEnd);
        request.setDueReturnDate(approvedEnd);
        request.setStatus(EventEquipmentRequestStatus.APPROVED);
        request.setReviewedBy(committee);
        request.setCommitteeNotes(committeeNotes);
        request.setApprovedAt(LocalDateTime.now());

        request = requestRepository.save(request);

        if (subEquipmentEntries != null) {
            request.getSubItems().clear();
            requestRepository.save(request);
            buildAndSaveSubItems(request, subEquipmentEntries, approvedStart, approvedEnd, id);
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
    public Page<EventEquipmentRequest> getAllRequests(String search, String status, int page, int size) {
        int clampedPage = Math.max(page, 0);
        int clampedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);
        String s = (search == null) ? "" : search.trim();
        String st = (status == null) ? "" : status.trim();
        return requestRepository.searchRequests(s, st, pageable);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void buildAndSaveSubItems(EventEquipmentRequest request,
            List<EquipmentRequestSubItemRequest> entries,
            LocalDate startDate, LocalDate endDate, Long excludeRequestId) {
        if (entries == null || entries.isEmpty()) return;
        List<EventEquipmentRequestSubItem> subItems = new ArrayList<>();
        for (EquipmentRequestSubItemRequest entry : entries) {
            SubEquipment sub = subEquipmentRepository.findById(entry.subEquipmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Sub-equipment not found: " + entry.subEquipmentId()));
            int committed = subItemRepository.sumCommittedQuantity(
                    sub.getSubEquipmentId(), startDate, endDate, BLOCKING_STATUSES, excludeRequestId);
            int held = subEquipmentQuantityHoldRepository.sumHeldQuantity(
                    sub.getSubEquipmentId(), startDate, endDate, 0L);
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
