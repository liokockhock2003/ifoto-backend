package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.*;
import com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO.EquipmentRequestSubItemRequest;
import com.ifoto.ifoto_backend.model.*;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import com.ifoto.ifoto_backend.model.enumerator.MainEquipmentStatusType;
import com.ifoto.ifoto_backend.repository.*;
import com.ifoto.ifoto_backend.service.EventEquipmentRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventEquipmentRequestServiceTest {

    @Mock private EventEquipmentRequestRepository requestRepository;
    @Mock private EventEquipmentRequestItemRepository requestItemRepository;
    @Mock private EventEquipmentRequestSubItemRepository subItemRepository;
    @Mock private EventRepository eventRepository;
    @Mock private MainEquipmentRepository mainEquipmentRepository;
    @Mock private SubEquipmentRepository subEquipmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private MainEquipmentStatusRepository mainEquipmentStatusRepository;
    @Mock private SubEquipmentQuantityHoldRepository subEquipmentQuantityHoldRepository;
    @Mock private EquipmentRentalItemRepository rentalItemRepository;

    @InjectMocks private EventEquipmentRequestService service;

    private User requester;
    private User committeeUser;
    private MainEquipment equipment;
    private Event event;
    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bufferMinutes", 60);

        start = LocalDateTime.now().plusDays(5);
        end = LocalDateTime.now().plusDays(7);

        requester = User.builder().id(1L).username("alice").build();
        committeeUser = User.builder().id(2L).username("comm").build();
        equipment = MainEquipment.builder().mainEquipmentId(100L).brand("Canon").model("R5").build();
        event = Event.builder().eventId(10L).startDatetime(start).endDatetime(end)
                .eventCommittee(new ArrayList<>(List.of(requester))).build();
    }

    // ── submitRequest ────────────────────────────────────────────────────────

    @Test
    void submitRequest_userNotCommitteeMember_throwsForbidden() {
        User outsider = User.builder().id(99L).username("outsider").build();
        when(userRepository.findByUsername("outsider")).thenReturn(Optional.of(outsider));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitRequest(10L, List.of(100L), null, null, "outsider"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void submitRequest_validMember_createsRequestWithPendingReview() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(requester));
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        when(requestItemRepository.existsConflictingRequest(anyLong(), anyLong(), anyList(), any(), any()))
                .thenReturn(false);
        when(rentalItemRepository.existsConflictingApprovedRental(anyLong(), any(), any(), anyLong(), anyList()))
                .thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(anyLong(), any(), any())).thenReturn(false);

        EventEquipmentRequest saved = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.PENDING_REVIEW)
                .requestedBy(requester).event(event)
                .startDatetime(start).endDatetime(end).build();
        when(requestRepository.save(any())).thenReturn(saved);
        when(requestItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.submitRequest(10L, List.of(100L), "notes", null, "alice");

        assertEquals(EventEquipmentRequestStatus.PENDING_REVIEW, result.getStatus());
        assertEquals(requester, result.getRequestedBy());
    }

    // ── cancelRequest ────────────────────────────────────────────────────────

    @Test
    void cancelRequest_differentUser_throwsForbidden() {
        User other = User.builder().id(99L).username("other").build();
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).requestedBy(requester)
                .status(EventEquipmentRequestStatus.PENDING_REVIEW).build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(other));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cancelRequest(1L, "other"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void cancelRequest_statusIsActive_throwsBadRequest() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).requestedBy(requester)
                .status(EventEquipmentRequestStatus.ACTIVE).build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(requester));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cancelRequest(1L, "alice"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void cancelRequest_pendingReview_cancelsSuccessfully() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).requestedBy(requester)
                .status(EventEquipmentRequestStatus.PENDING_REVIEW).build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(requester));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.cancelRequest(1L, "alice");

        assertEquals(EventEquipmentRequestStatus.CANCELLED, result.getStatus());
    }

    @Test
    void cancelRequest_approved_cancelsSuccessfully() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).requestedBy(requester)
                .status(EventEquipmentRequestStatus.APPROVED).build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(requester));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.cancelRequest(1L, "alice");

        assertEquals(EventEquipmentRequestStatus.CANCELLED, result.getStatus());
    }

    // ── reviewRequest ────────────────────────────────────────────────────────

    @Test
    void reviewRequest_notPendingReview_throwsBadRequest() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED).build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(committeeUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reviewRequest(1L, "APPROVE", List.of(100L), null, null, null, null, null, "comm"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void reviewRequest_actionReject_setsRejectedWithReason() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.PENDING_REVIEW).build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(committeeUser));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.reviewRequest(
                1L, "REJECT", null, null, "not enough stock", null, null, null, "comm");

        assertEquals(EventEquipmentRequestStatus.REJECTED, result.getStatus());
        assertEquals("not enough stock", result.getRejectionReason());
        assertEquals(committeeUser, result.getReviewedBy());
    }

    @Test
    void reviewRequest_actionRejectLowercase_setsRejected() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.PENDING_REVIEW).build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(committeeUser));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.reviewRequest(
                1L, "reject", null, null, "reason", null, null, null, "comm");

        assertEquals(EventEquipmentRequestStatus.REJECTED, result.getStatus());
    }

    @Test
    void reviewRequest_invalidAction_throwsBadRequest() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.PENDING_REVIEW).build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(committeeUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reviewRequest(1L, "GARBAGE", List.of(100L), null, null, null, null, null, "comm"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void reviewRequest_actionApprove_setsApprovedStatus() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.PENDING_REVIEW)
                .event(event).items(new ArrayList<>()).subItems(new ArrayList<>()).build();
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(committeeUser));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        when(requestItemRepository.existsConflictingRequest(anyLong(), anyLong(), anyList(), any(), any()))
                .thenReturn(false);
        when(rentalItemRepository.existsConflictingApprovedRental(anyLong(), any(), any(), anyLong(), anyList()))
                .thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(anyLong(), any(), any())).thenReturn(false);
        when(requestItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.reviewRequest(
                1L, "APPROVE", List.of(100L), null, null, null, start, end, "comm");

        assertEquals(EventEquipmentRequestStatus.APPROVED, result.getStatus());
        assertNotNull(result.getApprovedAt());
        assertEquals(committeeUser, result.getReviewedBy());
    }

    // ── markPickedUp ─────────────────────────────────────────────────────────

    @Test
    void markPickedUp_statusPendingReview_throwsBadRequest() {
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.PENDING_REVIEW);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.markPickedUp(1L, "comm"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void markPickedUp_nonApproverWhenApproverStillActive_throwsForbidden() {
        User activeApprover = equipmentCommitteeUser(10L, "approver");
        User otherComm = User.builder().id(2L).username("other").build();
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.APPROVED);
        request.setReviewedBy(activeApprover);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherComm));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.markPickedUp(1L, "other"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void markPickedUp_statusApproved_byOriginalApprover_setsPickedUpAt() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.APPROVED);
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.markPickedUp(1L, "comm");

        assertNotNull(result.getPickedUpAt());
    }

    @Test
    void markPickedUp_statusActive_noReviewer_setsPickedUpAt() {
        // reviewer=null → approverStillActive=false → fallback passes for any caller
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.ACTIVE);
        request.setReviewedBy(null);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(committeeUser));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.markPickedUp(1L, "comm");

        assertNotNull(result.getPickedUpAt());
    }

    @Test
    void markPickedUp_approverNoLongerEquipmentCommittee_fallbackPasses() {
        // reviewedBy is set but lost their role → approverStillActive=false → any caller can act
        User formerApprover = User.builder().id(10L).username("former").isActive(true)
                .roles(Set.of(Role.builder().name("ROLE_STUDENT").build())).build();
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.APPROVED);
        request.setReviewedBy(formerApprover);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(committeeUser));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.markPickedUp(1L, "comm");

        assertNotNull(result.getPickedUpAt());
    }

    // ── markActive ────────────────────────────────────────────────────────────

    @Test
    void markActive_statusNotApproved_throwsBadRequest() {
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.PENDING_REVIEW);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.markActive(1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void markActive_statusApproved_setsActiveAndTimestamp() {
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.APPROVED);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.markActive(1L);

        assertEquals(EventEquipmentRequestStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getActiveAt());
    }

    // ── markReturned ──────────────────────────────────────────────────────────

    @Test
    void markReturned_statusNotActive_throwsBadRequest() {
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.APPROVED);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.markReturned(1L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void markReturned_statusActive_setsReturnedAndTimestamp() {
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.ACTIVE);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.markReturned(1L);

        assertEquals(EventEquipmentRequestStatus.RETURNED, result.getStatus());
        assertNotNull(result.getReturnedAt());
    }

    // ── updateLogistics ───────────────────────────────────────────────────────

    @Test
    void updateLogistics_statusNotApproved_throwsBadRequest() {
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.ACTIVE);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateLogistics(1L, "comm", start, end));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateLogistics_nonApproverWhenApproverStillActive_throwsForbidden() {
        User activeApprover = equipmentCommitteeUser(10L, "approver");
        User other = User.builder().id(3L).username("other").build();
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(activeApprover);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(other));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateLogistics(1L, "other", start, end));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void updateLogistics_startNotBeforeEnd_throwsBadRequest() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateLogistics(1L, "comm", end, start));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateLogistics_eventRequestConflict_throwsConflict() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(requestItemRepository.existsConflictingRequest(anyLong(), anyLong(), anyList(), any(), any()))
                .thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateLogistics(1L, "comm", start, end));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateLogistics_rentalConflict_throwsConflict() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(requestItemRepository.existsConflictingRequest(anyLong(), anyLong(), anyList(), any(), any()))
                .thenReturn(false);
        when(rentalItemRepository.existsConflictingApprovedRental(anyLong(), any(), any(), anyLong(), anyList()))
                .thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateLogistics(1L, "comm", start, end));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateLogistics_statusConflict_throwsConflict() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(requestItemRepository.existsConflictingRequest(anyLong(), anyLong(), anyList(), any(), any()))
                .thenReturn(false);
        when(rentalItemRepository.existsConflictingApprovedRental(anyLong(), any(), any(), anyLong(), anyList()))
                .thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(anyLong(), any(), any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateLogistics(1L, "comm", start, end));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateLogistics_validUpdate_updatesDatetimesAndDuration() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        LocalDateTime newPickup = start.plusHours(1);
        LocalDateTime newReturn = end.plusHours(1);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(requestItemRepository.existsConflictingRequest(anyLong(), anyLong(), anyList(), any(), any()))
                .thenReturn(false);
        when(rentalItemRepository.existsConflictingApprovedRental(anyLong(), any(), any(), anyLong(), anyList()))
                .thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(anyLong(), any(), any())).thenReturn(false);
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.updateLogistics(1L, "comm", newPickup, newReturn);

        assertEquals(newPickup, result.getPickupDatetime());
        assertEquals(newReturn, result.getReturnDatetime());
        assertNotNull(result.getDurationDays());
    }

    // ── updateEquipment ───────────────────────────────────────────────────────

    @Test
    void updateEquipment_statusNotApproved_throwsBadRequest() {
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.ACTIVE);
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(1L, "comm", List.of(100L), null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateEquipment_nonApproverWhenApproverStillActive_throwsForbidden() {
        User activeApprover = equipmentCommitteeUser(10L, "approver");
        User other = User.builder().id(3L).username("other").build();
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(activeApprover);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(other));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(1L, "other", List.of(100L), null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void updateEquipment_equipmentNotFound_throwsNotFound() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(mainEquipmentRepository.findById(200L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(1L, "comm", List.of(200L), null));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateEquipment_eventRequestConflict_throwsConflict() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        when(requestItemRepository.existsConflictingRequest(anyLong(), anyLong(), anyList(), any(), any()))
                .thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(1L, "comm", List.of(100L), null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateEquipment_rentalConflict_throwsConflict() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        when(requestItemRepository.existsConflictingRequest(anyLong(), anyLong(), anyList(), any(), any()))
                .thenReturn(false);
        when(rentalItemRepository.existsConflictingApprovedRental(anyLong(), any(), any(), anyLong(), anyList()))
                .thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(1L, "comm", List.of(100L), null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateEquipment_statusConflict_throwsConflict() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        when(requestItemRepository.existsConflictingRequest(anyLong(), anyLong(), anyList(), any(), any()))
                .thenReturn(false);
        when(rentalItemRepository.existsConflictingApprovedRental(anyLong(), any(), any(), anyLong(), anyList()))
                .thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(anyLong(), any(), any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(1L, "comm", List.of(100L), null));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateEquipment_noSubItems_replacesItemsAndSaves() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        stubNoConflicts();
        when(requestItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.updateEquipment(1L, "comm", List.of(100L), null);

        assertEquals(EventEquipmentRequestStatus.APPROVED, result.getStatus());
        assertEquals(1, result.getItems().size());
        assertEquals(100L, result.getItems().get(0).getMainEquipment().getMainEquipmentId());
    }

    @Test
    void updateEquipment_withSubItems_replacesSubItemsToo() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);
        SubEquipment sub = SubEquipment.builder().subEquipmentId(20L).type("Flash").brand("Godox")
                .totalQuantity(5).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        stubNoConflicts();
        when(requestItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subItemRepository.sumCommittedQuantity(any(), any(), any(), anyList(), anyLong())).thenReturn(0);
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), anyLong())).thenReturn(0);
        when(subItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.updateEquipment(
                1L, "comm", List.of(100L), List.of(new EquipmentRequestSubItemRequest(20L, 2)));

        assertEquals(1, result.getSubItems().size());
        assertEquals(20L, result.getSubItems().get(0).getSubEquipment().getSubEquipmentId());
        assertEquals(2, result.getSubItems().get(0).getBorrowedQuantity());
    }

    // ── buildAndSaveSubItems (via updateEquipment) ────────────────────────────

    @Test
    void buildAndSaveSubItems_subEquipmentNotFound_throwsNotFound() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        stubNoConflicts();
        when(requestItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subEquipmentRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(1L, "comm", List.of(100L),
                        List.of(new EquipmentRequestSubItemRequest(99L, 1))));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void buildAndSaveSubItems_quantityExceeded_throwsConflict() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);
        // totalQuantity=2, committed=1, held=1, requested=1 → 3 > 2
        SubEquipment sub = SubEquipment.builder().subEquipmentId(20L).type("Flash").brand("Godox")
                .totalQuantity(2).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        stubNoConflicts();
        when(requestItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subItemRepository.sumCommittedQuantity(any(), any(), any(), anyList(), anyLong())).thenReturn(1);
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), anyLong())).thenReturn(1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(1L, "comm", List.of(100L),
                        List.of(new EquipmentRequestSubItemRequest(20L, 1))));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void buildAndSaveSubItems_quantityFits_subItemSaved() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);
        // totalQuantity=5, committed=1, held=1, requested=2 → 4 ≤ 5 → ok
        SubEquipment sub = SubEquipment.builder().subEquipmentId(20L).type("Flash").brand("Godox")
                .totalQuantity(5).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        stubNoConflicts();
        when(requestItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subItemRepository.sumCommittedQuantity(any(), any(), any(), anyList(), anyLong())).thenReturn(1);
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), anyLong())).thenReturn(1);
        when(subItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.updateEquipment(
                1L, "comm", List.of(100L), List.of(new EquipmentRequestSubItemRequest(20L, 2)));

        assertEquals(1, result.getSubItems().size());
        assertEquals(2, result.getSubItems().get(0).getBorrowedQuantity());
    }

    @Test
    void buildAndSaveSubItems_emptyEntries_skipped() {
        User approver = equipmentCommitteeUser(2L, "comm");
        EventEquipmentRequest request = approvedRequestWithEquipment();
        request.setReviewedBy(approver);

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("comm")).thenReturn(Optional.of(approver));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        stubNoConflicts();
        when(requestItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EventEquipmentRequest result = service.updateEquipment(
                1L, "comm", List.of(100L), List.of());

        assertTrue(result.getSubItems().isEmpty());
        verify(subEquipmentRepository, never()).findById(any());
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private EventEquipmentRequest requestWithStatus(EventEquipmentRequestStatus status) {
        return EventEquipmentRequest.builder()
                .id(1L).status(status)
                .startDatetime(start).endDatetime(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>())
                .build();
    }

    private EventEquipmentRequest approvedRequestWithEquipment() {
        EventEquipmentRequestItem item = EventEquipmentRequestItem.builder()
                .mainEquipment(equipment).build();
        return EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .startDatetime(start).endDatetime(end)
                .pickupDatetime(start).returnDatetime(end)
                .items(new ArrayList<>(List.of(item))).subItems(new ArrayList<>())
                .build();
    }

    private User equipmentCommitteeUser(Long id, String username) {
        return User.builder().id(id).username(username).isActive(true)
                .roles(Set.of(Role.builder().name("ROLE_EQUIPMENT_COMMITTEE").build()))
                .build();
    }

    private void stubNoConflicts() {
        when(requestItemRepository.existsConflictingRequest(anyLong(), anyLong(), anyList(), any(), any()))
                .thenReturn(false);
        when(rentalItemRepository.existsConflictingApprovedRental(anyLong(), any(), any(), anyLong(), anyList()))
                .thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(anyLong(), any(), any())).thenReturn(false);
    }

    // ── getEquipmentSchedules ─────────────────────────────────────────────────

    @Test
    void getEquipmentSchedules_notOwnerNotCommittee_throwsForbidden() {
        User stranger = User.builder().id(99L).username("stranger").build();
        EventEquipmentRequest request = requestWithStatus(EventEquipmentRequestStatus.APPROVED);
        request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .requestedBy(requester).startDatetime(start).endDatetime(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>()).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("stranger")).thenReturn(Optional.of(stranger));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getEquipmentSchedules(1L, null, null, "stranger"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getEquipmentSchedules_ownerWithNoCommitteeRole_succeeds() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .requestedBy(requester).startDatetime(start).endDatetime(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>()).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(requester));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(1L, List.of(), List.of(), "alice");

        assertNotNull(result);
        assertTrue(result.mainEquipment().isEmpty());
        assertTrue(result.subEquipment().isEmpty());
    }

    @Test
    void getEquipmentSchedules_equipmentCommitteeNonOwner_succeeds() {
        User committee = userWithRole("committee", "ROLE_EQUIPMENT_COMMITTEE");
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .requestedBy(requester).startDatetime(start).endDatetime(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>()).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(committee));

        assertNotNull(service.getEquipmentSchedules(1L, List.of(), List.of(), "committee"));
    }

    @Test
    void getEquipmentSchedules_eventCommitteeNonOwner_succeeds() {
        User committee = userWithRole("evtcomm", "ROLE_EVENT_COMMITTEE");
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .requestedBy(requester).startDatetime(start).endDatetime(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>()).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("evtcomm")).thenReturn(Optional.of(committee));

        assertNotNull(service.getEquipmentSchedules(1L, List.of(), List.of(), "evtcomm"));
    }

    @Test
    void getEquipmentSchedules_highCommitteeNonOwner_succeeds() {
        User committee = userWithRole("highcomm", "ROLE_HIGH_COMMITTEE");
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .requestedBy(requester).startDatetime(start).endDatetime(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>()).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("highcomm")).thenReturn(Optional.of(committee));

        assertNotNull(service.getEquipmentSchedules(1L, List.of(), List.of(), "highcomm"));
    }

    @Test
    void getEquipmentSchedules_nullIds_fallsBackToEntityItems() {
        EventEquipmentRequestItem item = EventEquipmentRequestItem.builder()
                .mainEquipment(equipment).build();
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .requestedBy(requester).startDatetime(start).endDatetime(end)
                .items(new ArrayList<>(List.of(item))).subItems(new ArrayList<>()).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(requester));
        when(mainEquipmentStatusRepository.findAllByEquipmentIds(List.of(100L))).thenReturn(List.of());
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(1L, null, null, "alice");

        assertEquals(1, result.mainEquipment().size());
        assertEquals(100L, result.mainEquipment().get(0).mainEquipmentId());
    }

    @Test
    void getEquipmentSchedules_emptyMainIds_skipsStatusRepoCall() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .requestedBy(requester).startDatetime(start).endDatetime(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>()).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(requester));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(1L, List.of(), List.of(), "alice");

        verify(mainEquipmentStatusRepository, never()).findAllByEquipmentIds(any());
        assertTrue(result.mainEquipment().isEmpty());
    }

    @Test
    void getEquipmentSchedules_emptySubIds_skipsHoldRepoCall() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .requestedBy(requester).startDatetime(start).endDatetime(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>()).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(requester));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(1L, List.of(), List.of(), "alice");

        verify(subEquipmentQuantityHoldRepository, never()).findAllBySubEquipmentIds(any());
        assertTrue(result.subEquipment().isEmpty());
    }

    @Test
    void getEquipmentSchedules_mainEquipmentNotFound_throwsNotFound() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .requestedBy(requester).startDatetime(start).endDatetime(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>()).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(requester));
        when(mainEquipmentStatusRepository.findAllByEquipmentIds(any())).thenReturn(List.of());
        when(mainEquipmentRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getEquipmentSchedules(1L, List.of(999L), List.of(), "alice"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getEquipmentSchedules_subEquipmentNotFound_throwsNotFound() {
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .requestedBy(requester).startDatetime(start).endDatetime(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>()).build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(requester));
        when(subEquipmentQuantityHoldRepository.findAllBySubEquipmentIds(any())).thenReturn(List.of());
        when(subEquipmentRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getEquipmentSchedules(1L, List.of(), List.of(999L), "alice"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getEquipmentSchedules_happyPath_returnsStatusesAndHoldsAndBorrowedQty() {
        SubEquipment sub = SubEquipment.builder().subEquipmentId(20L)
                .type("Flash").brand("Godox").totalQuantity(5).build();
        EventEquipmentRequestSubItem subItem = EventEquipmentRequestSubItem.builder()
                .subEquipment(sub).borrowedQuantity(3).build();
        EventEquipmentRequest request = EventEquipmentRequest.builder()
                .id(1L).status(EventEquipmentRequestStatus.APPROVED)
                .requestedBy(requester).startDatetime(start).endDatetime(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>(List.of(subItem))).build();

        LocalDateTime now = LocalDateTime.now();
        MainEquipmentStatus status = MainEquipmentStatus.builder()
                .id(1L).mainEquipment(equipment).statusType(MainEquipmentStatusType.IN_USE)
                .startDatetime(now).endDatetime(now.plusDays(1)).notes("test status").build();
        SubEquipmentQuantityHold hold = SubEquipmentQuantityHold.builder()
                .id(2L).subEquipment(sub).quantity(2)
                .startDatetime(now).endDatetime(now.plusDays(1)).notes("test hold").build();

        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(requester));
        when(mainEquipmentStatusRepository.findAllByEquipmentIds(List.of(100L))).thenReturn(List.of(status));
        when(subEquipmentQuantityHoldRepository.findAllBySubEquipmentIds(List.of(20L))).thenReturn(List.of(hold));
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(equipment));
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(
                1L, List.of(100L), List.of(20L), "alice");

        assertEquals(1, result.mainEquipment().size());
        MainEquipmentScheduleEntry mainEntry = result.mainEquipment().get(0);
        assertEquals(100L, mainEntry.mainEquipmentId());
        assertEquals(1, mainEntry.statuses().size());
        assertEquals(MainEquipmentStatusType.IN_USE, mainEntry.statuses().get(0).statusType());

        assertEquals(1, result.subEquipment().size());
        SubEquipmentScheduleEntry subEntry = result.subEquipment().get(0);
        assertEquals(20L, subEntry.subEquipmentId());
        assertEquals(3, subEntry.borrowedQuantity());
        assertEquals(1, subEntry.holds().size());
        assertEquals(2, subEntry.holds().get(0).quantity());
    }

    private User userWithRole(String username, String roleName) {
        return User.builder().id(50L).username(username).isActive(true)
                .roles(Set.of(Role.builder().name(roleName).build()))
                .build();
    }
}
