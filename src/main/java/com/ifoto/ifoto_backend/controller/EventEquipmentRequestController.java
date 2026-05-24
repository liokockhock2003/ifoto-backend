package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO.*;
import com.ifoto.ifoto_backend.model.EventEquipmentRequest;
import com.ifoto.ifoto_backend.model.EventEquipmentRequestItem;
import com.ifoto.ifoto_backend.model.EventEquipmentRequestSubItem;
import com.ifoto.ifoto_backend.scheduler.RentalScheduler;
import com.ifoto.ifoto_backend.service.EventEquipmentRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/event-equipment-requests")
@RequiredArgsConstructor
public class EventEquipmentRequestController {

    private final EventEquipmentRequestService requestService;
    private final RentalScheduler rentalScheduler;

    // ── Event Committee endpoints ─────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<EquipmentRequestResponse> submitRequest(
            @Valid @RequestBody EquipmentRequestRequest req, Authentication auth) {
        EventEquipmentRequest request = requestService.submitRequest(
                req.eventId(), req.equipmentIds(), req.startDate(), req.endDate(),
                req.notes(), req.subEquipmentEntries(), auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(request));
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<EquipmentRequestResponse>> getRequestsByEvent(
            @PathVariable Long eventId, Authentication auth) {
        return ResponseEntity.ok(requestService.getRequestsByEvent(eventId, auth.getName())
                .stream().map(this::toResponse).toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<EquipmentRequestResponse> cancelRequest(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(toResponse(requestService.cancelRequest(id, auth.getName())));
    }

    // ── Equipment Committee endpoints ─────────────────────────────────────────

    @GetMapping
    public ResponseEntity<Page<EquipmentRequestResponse>> getAllRequests(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                requestService.getAllRequests(search, status, page, size).map(this::toResponse));
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<EquipmentRequestResponse> reviewRequest(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentRequestReviewRequest req,
            Authentication auth) {
        EventEquipmentRequest request = requestService.reviewRequest(
                id, req.action(), req.approvedStartDate(), req.approvedEndDate(),
                req.equipmentIds(), req.subEquipmentEntries(),
                req.rejectionReason(), req.committeeNotes(), auth.getName());
        return ResponseEntity.ok(toResponse(request));
    }

    @PatchMapping("/{id}/mark-returned")
    public ResponseEntity<EquipmentRequestResponse> markReturned(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(requestService.markReturned(id)));
    }

    @PostMapping("/trigger-active")
    public ResponseEntity<Void> triggerActiveCheck() {
        rentalScheduler.markActiveEventRequests();
        return ResponseEntity.ok().build();
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private EquipmentRequestResponse toResponse(EventEquipmentRequest r) {
        return new EquipmentRequestResponse(
                r.getId(),
                r.getRequestNumber(),
                r.getEvent().getEventId(),
                r.getEvent().getEventName(),
                r.getRequestedBy().getUsername(),
                r.getReviewedBy() != null ? r.getReviewedBy().getUsername() : null,
                r.getStatus().name(),
                r.getRequestedStartDate(),
                r.getRequestedEndDate(),
                r.getApprovedStartDate(),
                r.getApprovedEndDate(),
                r.getDurationDays(),
                r.getRejectionReason(),
                r.getCommitteeNotes(),
                r.getRequesterNotes(),
                r.getItems().stream().map(this::toItemResponse).toList(),
                r.getSubItems().stream().map(this::toSubItemResponse).toList(),
                r.getCreatedAt()
        );
    }

    private EquipmentRequestItemResponse toItemResponse(EventEquipmentRequestItem item) {
        return new EquipmentRequestItemResponse(
                item.getId(),
                item.getMainEquipment().getMainEquipmentId(),
                item.getMainEquipment().getEquipmentType(),
                item.getMainEquipment().getBrand(),
                item.getMainEquipment().getModel(),
                item.getMainEquipment().getSerialNumber()
        );
    }

    private EquipmentRequestSubItemResponse toSubItemResponse(EventEquipmentRequestSubItem si) {
        return new EquipmentRequestSubItemResponse(
                si.getId(),
                si.getSubEquipment().getSubEquipmentId(),
                si.getSubEquipment().getType(),
                si.getSubEquipment().getEquipmentType(),
                si.getSubEquipment().getCameraModel(),
                si.getSubEquipment().getBrand(),
                si.getBorrowedQuantity()
        );
    }
}
