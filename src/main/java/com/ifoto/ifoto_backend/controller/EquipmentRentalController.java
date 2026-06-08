package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.EquipmentSchedulesResponse;
import com.ifoto.ifoto_backend.dto.EquipmentRentalDTO.*;
import com.ifoto.ifoto_backend.dto.PaymentDTO.InitiatePaymentRequest;
import com.ifoto.ifoto_backend.dto.PaymentDTO.PaymentResponse;
import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.EquipmentRentalItem;
import com.ifoto.ifoto_backend.model.EquipmentRentalSubItem;
import com.ifoto.ifoto_backend.model.Payment;
import com.ifoto.ifoto_backend.scheduler.RentalScheduler;
import com.ifoto.ifoto_backend.service.EquipmentRentalService;
import com.ifoto.ifoto_backend.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rentals")
@RequiredArgsConstructor
public class EquipmentRentalController {

    private final EquipmentRentalService rentalService;
    private final PaymentService paymentService;
    private final RentalScheduler rentalScheduler;

    // ── Renter endpoints ──────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<RentalResponse> submitRental(
            @Valid @RequestBody RentalRequest req, Authentication auth) {
        EquipmentRental rental = rentalService.submitRental(
                req.equipmentIds(), req.startDate(), req.endDate(), req.notes(), auth.getName(),
                req.subEquipmentEntries());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(rental));
    }

    @GetMapping("/my")
    public ResponseEntity<List<RentalResponse>> getMyRentals(Authentication auth) {
        return ResponseEntity.ok(rentalService.getMyRentals(auth.getName())
                .stream().map(this::toResponse).toList());
    }

    @GetMapping("/equipment/{equipmentId}")
    public ResponseEntity<List<RentalResponse>> getRentalsByEquipment(@PathVariable Long equipmentId) {
        return ResponseEntity.ok(rentalService.getRentalsByEquipment(equipmentId)
                .stream().map(this::toResponse).toList());
    }

    @GetMapping("/sub-equipment/{subEquipmentId}")
    public ResponseEntity<List<RentalResponse>> getRentalsBySubEquipment(@PathVariable Long subEquipmentId) {
        return ResponseEntity.ok(rentalService.getRentalsBySubEquipment(subEquipmentId)
                .stream().map(this::toResponse).toList());
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @PathVariable Long id,
            @RequestBody InitiatePaymentRequest req,
            Authentication auth) {
        EquipmentRental rental = rentalService.initiatePayment(id, req.paymentMethod(), auth.getName());
        Payment payment = paymentService.findByRentalId(id).orElse(null);
        return ResponseEntity.ok(new PaymentResponse(
                payment != null ? payment.getId() : null,
                req.paymentMethod().toUpperCase(),
                payment != null ? payment.getStatus().name() : "PENDING",
                rental.getTotalAmount(),
                payment != null ? payment.getBillUrl() : null,
                payment != null ? payment.getBillId() : null,
                "ONLINE".equalsIgnoreCase(req.paymentMethod())
                        ? "Proceed to payment URL"
                        : "Payment initiated — await committee confirmation"
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<RentalResponse> cancelRental(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(toResponse(rentalService.cancelRental(id, auth.getName())));
    }

    // ── Committee endpoints ───────────────────────────────────────────────────

    @GetMapping("/my-approvals")
    public ResponseEntity<List<RentalResponse>> getMyApprovals(Authentication auth) {
        return ResponseEntity.ok(rentalService.getMyApprovals(auth.getName())
                .stream().map(this::toResponse).toList());
    }

    @GetMapping
    public ResponseEntity<Page<RentalResponse>> getAllRentals(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                rentalService.getAllRentals(search, status, page, size).map(this::toResponse));
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<RentalResponse> reviewRental(
            @PathVariable Long id,
            @Valid @RequestBody RentalReviewRequest req,
            Authentication auth) {
        EquipmentRental rental = rentalService.reviewRental(
                id, req.action(), req.equipmentIds(), req.subEquipmentEntries(),
                req.rejectionReason(), req.committeeNotes(), auth.getName(),
                req.pickupDatetime(), req.returnDatetime());
        return ResponseEntity.ok(toResponse(rental));
    }

    @PatchMapping("/{id}/mark-picked-up")
    public ResponseEntity<RentalResponse> markPickedUp(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(toResponse(rentalService.markPickedUp(id, auth.getName())));
    }

    @PatchMapping("/{id}/logistics")
    public ResponseEntity<RentalResponse> updateLogistics(
            @PathVariable Long id,
            @Valid @RequestBody RentalLogisticsRequest req,
            Authentication auth) {
        return ResponseEntity.ok(toResponse(
                rentalService.updateLogistics(id, auth.getName(), req.pickupDatetime(), req.returnDatetime())));
    }

    @PatchMapping("/{id}/equipment")
    public ResponseEntity<RentalResponse> updateEquipment(
            @PathVariable Long id,
            @Valid @RequestBody RentalEquipmentUpdateRequest req,
            Authentication auth) {
        return ResponseEntity.ok(toResponse(
                rentalService.updateEquipment(id, auth.getName(), req.equipmentIds(), req.subEquipmentEntries())));
    }

    @PatchMapping("/{id}/confirm-manual-payment")
    public ResponseEntity<RentalResponse> confirmManualPayment(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(toResponse(rentalService.confirmManualPayment(id, auth.getName())));
    }

    @PatchMapping("/{id}/mark-active")
    public ResponseEntity<RentalResponse> markActive(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(rentalService.markActive(id)));
    }

    @PatchMapping("/{id}/mark-returned")
    public ResponseEntity<RentalResponse> markReturned(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(rentalService.markReturned(id)));
    }

    @PostMapping("/trigger-active")
    public ResponseEntity<Void> triggerActiveCheck() {
        rentalScheduler.markActiveRentals();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/trigger-overdue")
    public ResponseEntity<Void> triggerOverdueCheck() {
        rentalScheduler.markOverdueRentals();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/equipment-schedules")
    public ResponseEntity<EquipmentSchedulesResponse> getEquipmentSchedules(
            @PathVariable Long id,
            @RequestParam(required = false) List<Long> mainEquipmentIds,
            @RequestParam(required = false) List<Long> subEquipmentIds,
            Authentication auth) {
        return ResponseEntity.ok(rentalService.getEquipmentSchedules(
                id, mainEquipmentIds, subEquipmentIds, auth.getName()));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private RentalResponse toResponse(EquipmentRental r) {
        return new RentalResponse(
                r.getId(),
                r.getRentalNumber(),
                r.getRenter().getUsername(),
                r.getStatus().name(),
                r.getPaymentMethod().name(),
                r.getPaymentStatus().name(),
                r.getProgramStartDate(),
                r.getProgramEndDate(),
                r.getPickupDatetime(),
                r.getReturnDatetime(),
                r.getDurationDays(),
                r.getTotalBaseAmount(),
                r.getTotalPenaltyAmount(),
                r.getTotalAmount(),
                r.getRejectionReason(),
                r.getCommitteeNotes(),
                r.getRenterNotes(),
                r.getPickedUpAt(),
                r.getItems().stream().map(this::toItemResponse).toList(),
                r.getSubItems().stream().map(this::toSubItemResponse).toList(),
                r.getCreatedAt(),
                r.getReviewedBy() != null ? r.getReviewedBy().getUsername() : null,
                r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null,
                r.getApprovedAt()
        );
    }

    private RentalSubItemResponse toSubItemResponse(EquipmentRentalSubItem subItem) {
        return new RentalSubItemResponse(
                subItem.getId(),
                subItem.getSubEquipment().getSubEquipmentId(),
                subItem.getSubEquipment().getType(),
                subItem.getSubEquipment().getEquipmentType(),
                subItem.getSubEquipment().getCameraModel(),
                subItem.getSubEquipment().getBrand(),
                subItem.getBorrowedQuantity(),
                subItem.getBaseAmount(),
                subItem.getLatePenaltyAmount(),
                subItem.getItemTotalAmount()
        );
    }

    private RentalItemResponse toItemResponse(EquipmentRentalItem item) {
        return new RentalItemResponse(
                item.getId(),
                item.getMainEquipment().getMainEquipmentId(),
                item.getMainEquipment().getEquipmentType(),
                item.getMainEquipment().getBrand(),
                item.getMainEquipment().getModel(),
                item.getMainEquipment().getSerialNumber(),
                item.getBaseAmount(),
                item.getLatePenaltyAmount(),
                item.getItemTotalAmount()
        );
    }
}
