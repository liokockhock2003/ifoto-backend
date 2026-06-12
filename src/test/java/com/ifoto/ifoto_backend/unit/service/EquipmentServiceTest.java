package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.*;
import com.ifoto.ifoto_backend.model.*;
import com.ifoto.ifoto_backend.model.enumerator.*;
import com.ifoto.ifoto_backend.repository.*;
import com.ifoto.ifoto_backend.service.EquipmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock private MainEquipmentRepository mainEquipmentRepository;
    @Mock private SubEquipmentRepository subEquipmentRepository;
    @Mock private RentalCategoryRepository rentalCategoryRepository;
    @Mock private EquipmentRentalSubItemRepository equipmentRentalSubItemRepository;
    @Mock private EquipmentRentalItemRepository equipmentRentalItemRepository;
    @Mock private EventEquipmentRequestSubItemRepository eventEquipmentRequestSubItemRepository;
    @Mock private EventEquipmentRequestItemRepository eventEquipmentRequestItemRepository;
    @Mock private MainEquipmentStatusRepository mainEquipmentStatusRepository;
    @Mock private SubEquipmentQuantityHoldRepository subEquipmentQuantityHoldRepository;

    @InjectMocks private EquipmentService service;

    private final LocalDateTime start = LocalDateTime.now().plusDays(1);
    private final LocalDateTime end   = LocalDateTime.now().plusDays(3);
    private final LocalDate startDate = LocalDate.now().plusDays(1);
    private final LocalDate endDate   = LocalDate.now().plusDays(3);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bufferMinutes", 60);

        // Default stubs so getAllEquipment / getAvailableEquipment tests don't need
        // to stub sub-quantity repo calls when subIds is empty.
        lenient().when(subEquipmentRepository.findAll()).thenReturn(List.of());
        lenient().when(subEquipmentRepository.findByIsForRentTrue()).thenReturn(List.of());
        lenient().when(equipmentRentalSubItemRepository
                .sumCommittedQuantityPerSubEquipment(any(), any(), anyList(), anyLong()))
                .thenReturn(List.of());
        lenient().when(eventEquipmentRequestSubItemRepository
                .sumCommittedQuantityPerSubEquipment(any(), any(), anyList()))
                .thenReturn(List.of());
        lenient().when(subEquipmentQuantityHoldRepository
                .sumHeldQuantityPerSubEquipment(any(), any()))
                .thenReturn(List.of());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubEmptySubBoundaries() {
        lenient().when(equipmentRentalSubItemRepository
                .findBoundaryReturnQuantities(anyList(), any(), anyList(), anyLong()))
                .thenReturn(List.of());
        lenient().when(equipmentRentalSubItemRepository
                .findBoundaryPickupQuantities(anyList(), any(), anyList(), anyLong()))
                .thenReturn(List.of());
        lenient().when(subEquipmentQuantityHoldRepository.findBoundaryEndQuantities(anyList(), any()))
                .thenReturn(List.of());
        lenient().when(subEquipmentQuantityHoldRepository.findBoundaryStartQuantities(anyList(), any()))
                .thenReturn(List.of());
        lenient().when(eventEquipmentRequestSubItemRepository
                .findBoundaryReturnQuantities(anyList(), any(), anyList()))
                .thenReturn(List.of());
        lenient().when(eventEquipmentRequestSubItemRepository
                .findBoundaryPickupQuantities(anyList(), any(), anyList()))
                .thenReturn(List.of());
    }

    private MainEquipment mainEq(Long id) {
        return MainEquipment.builder().mainEquipmentId(id).brand("Canon").model("R5").build();
    }

    private SubEquipment subEq(Long id) {
        return SubEquipment.builder().subEquipmentId(id).brand("SanDisk").totalQuantity(5).build();
    }

    // ── getAllEquipment (no-args) — status branching ───────────────────────────

    @Test
    void getAllEquipment_emptyRepo_skipsStatusCallsReturnsEmptyLists() {
        when(mainEquipmentRepository.findAll()).thenReturn(List.of());

        EquipmentListResponse result = service.getAllEquipment();

        assertTrue(result.mainEquipment().isEmpty());
        assertTrue(result.subEquipment().isEmpty());
        verify(mainEquipmentStatusRepository, never()).findActiveByEquipmentIds(any(), any());
        verify(equipmentRentalItemRepository, never()).findBookedEquipmentIds(any(), any(), any());
    }

    @Test
    void getAllEquipment_equipmentHasActiveStatus_statusTakenFromActiveMap() {
        MainEquipment eq = mainEq(1L);
        MainEquipmentStatus ms = MainEquipmentStatus.builder()
                .mainEquipment(eq).statusType(MainEquipmentStatusType.MAINTENANCE).build();

        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentStatusRepository.findActiveByEquipmentIds(anyList(), any()))
                .thenReturn(List.of(ms));
        when(equipmentRentalItemRepository.findBookedEquipmentIds(anyList(), any(), anyList()))
                .thenReturn(List.of());
        when(eventEquipmentRequestItemRepository.findBookedEquipmentIds(anyList(), any(), anyList()))
                .thenReturn(List.of());

        EquipmentListResponse result = service.getAllEquipment();

        assertEquals("MAINTENANCE", result.mainEquipment().get(0).status());
    }

    @Test
    void getAllEquipment_rentalBooked_notInActiveMap_returnsBooked() {
        MainEquipment eq = mainEq(1L);

        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentStatusRepository.findActiveByEquipmentIds(anyList(), any()))
                .thenReturn(List.of());
        when(equipmentRentalItemRepository.findBookedEquipmentIds(anyList(), any(), anyList()))
                .thenReturn(List.of(1L));
        when(eventEquipmentRequestItemRepository.findBookedEquipmentIds(anyList(), any(), anyList()))
                .thenReturn(List.of());

        assertEquals("BOOKED", service.getAllEquipment().mainEquipment().get(0).status());
    }

    @Test
    void getAllEquipment_eventBooked_notInActiveMap_returnsBooked() {
        MainEquipment eq = mainEq(1L);

        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentStatusRepository.findActiveByEquipmentIds(anyList(), any()))
                .thenReturn(List.of());
        when(equipmentRentalItemRepository.findBookedEquipmentIds(anyList(), any(), anyList()))
                .thenReturn(List.of());
        when(eventEquipmentRequestItemRepository.findBookedEquipmentIds(anyList(), any(), anyList()))
                .thenReturn(List.of(1L));

        assertEquals("BOOKED", service.getAllEquipment().mainEquipment().get(0).status());
    }

    @Test
    void getAllEquipment_notInAnyMap_returnsAvailable() {
        MainEquipment eq = mainEq(1L);

        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentStatusRepository.findActiveByEquipmentIds(anyList(), any()))
                .thenReturn(List.of());
        when(equipmentRentalItemRepository.findBookedEquipmentIds(anyList(), any(), anyList()))
                .thenReturn(List.of());
        when(eventEquipmentRequestItemRepository.findBookedEquipmentIds(anyList(), any(), anyList()))
                .thenReturn(List.of());

        assertEquals("AVAILABLE", service.getAllEquipment().mainEquipment().get(0).status());
    }

    // ── addMainEquipment ──────────────────────────────────────────────────────

    @Test
    void addMainEquipment_nullPricingCategoryId_savesWithNullCategoryNeverCallsRepo() {
        MainEquipmentRequest req = new MainEquipmentRequest(
                "Camera", null, "Canon", "R5", "SN001", null, null, null, true);
        when(mainEquipmentRepository.save(any())).thenReturn(mainEq(1L));

        service.addMainEquipment(req);

        ArgumentCaptor<MainEquipment> cap = ArgumentCaptor.forClass(MainEquipment.class);
        verify(mainEquipmentRepository).save(cap.capture());
        assertNull(cap.getValue().getPricingCategory());
        verify(rentalCategoryRepository, never()).findById(any());
    }

    @Test
    void addMainEquipment_withPricingCategoryId_resolvesAndAttachesCategory() {
        RentalCategory cat = mock(RentalCategory.class);
        when(rentalCategoryRepository.findById(5L)).thenReturn(Optional.of(cat));
        MainEquipmentRequest req = new MainEquipmentRequest(
                "Camera", null, "Canon", "R5", null, null, null, 5L, true);
        when(mainEquipmentRepository.save(any())).thenReturn(mainEq(1L));

        service.addMainEquipment(req);

        ArgumentCaptor<MainEquipment> cap = ArgumentCaptor.forClass(MainEquipment.class);
        verify(mainEquipmentRepository).save(cap.capture());
        assertEquals(cat, cap.getValue().getPricingCategory());
    }

    @Test
    void addMainEquipment_pricingCategoryNotFound_throwsNotFound() {
        when(rentalCategoryRepository.findById(99L)).thenReturn(Optional.empty());
        MainEquipmentRequest req = new MainEquipmentRequest(
                "Camera", null, "Canon", "R5", null, null, null, 99L, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addMainEquipment(req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void addMainEquipment_isForRentNull_defaultsToFalse() {
        MainEquipmentRequest req = new MainEquipmentRequest(
                "Camera", null, "Canon", "R5", null, null, null, null, null);
        when(mainEquipmentRepository.save(any())).thenReturn(mainEq(1L));

        service.addMainEquipment(req);

        ArgumentCaptor<MainEquipment> cap = ArgumentCaptor.forClass(MainEquipment.class);
        verify(mainEquipmentRepository).save(cap.capture());
        assertFalse(cap.getValue().isForRent());
    }

    // ── updateMainEquipment ───────────────────────────────────────────────────

    @Test
    void updateMainEquipment_notFound_throwsNotFound() {
        when(mainEquipmentRepository.findById(99L)).thenReturn(Optional.empty());
        MainEquipmentRequest req = new MainEquipmentRequest(
                "Camera", null, "Canon", "R5", null, null, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateMainEquipment(99L, req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateMainEquipment_nullPricingCategoryId_skipsResolution() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findById(1L)).thenReturn(Optional.of(eq));
        when(mainEquipmentStatusRepository.findActiveByEquipmentIds(anyList(), any())).thenReturn(List.of());
        when(mainEquipmentStatusRepository.findUpcomingByEquipmentIds(anyList(), any())).thenReturn(List.of());
        when(mainEquipmentRepository.save(any())).thenReturn(eq);
        MainEquipmentRequest req = new MainEquipmentRequest(
                "Camera", null, "Canon", "R5", null, null, null, null, null);

        service.updateMainEquipment(1L, req);

        verify(rentalCategoryRepository, never()).findById(any());
    }

    @Test
    void updateMainEquipment_nullIsForRent_doesNotChangeFlag() {
        MainEquipment eq = mainEq(1L);
        eq.setForRent(true);
        when(mainEquipmentRepository.findById(1L)).thenReturn(Optional.of(eq));
        when(mainEquipmentStatusRepository.findActiveByEquipmentIds(anyList(), any())).thenReturn(List.of());
        when(mainEquipmentStatusRepository.findUpcomingByEquipmentIds(anyList(), any())).thenReturn(List.of());
        when(mainEquipmentRepository.save(any())).thenReturn(eq);
        MainEquipmentRequest req = new MainEquipmentRequest(
                "Camera", null, "Canon", "R5", null, null, null, null, null);

        service.updateMainEquipment(1L, req);

        assertTrue(eq.isForRent());
    }

    @Test
    void updateMainEquipment_withIsForRentFalse_updatesFlag() {
        MainEquipment eq = mainEq(1L);
        eq.setForRent(true);
        when(mainEquipmentRepository.findById(1L)).thenReturn(Optional.of(eq));
        when(mainEquipmentStatusRepository.findActiveByEquipmentIds(anyList(), any())).thenReturn(List.of());
        when(mainEquipmentStatusRepository.findUpcomingByEquipmentIds(anyList(), any())).thenReturn(List.of());
        when(mainEquipmentRepository.save(any())).thenReturn(eq);
        MainEquipmentRequest req = new MainEquipmentRequest(
                "Camera", null, "Canon", "R5", null, null, null, null, false);

        service.updateMainEquipment(1L, req);

        assertFalse(eq.isForRent());
    }

    @Test
    void updateMainEquipment_withPricingCategoryId_resolvesAndSetsCategory() {
        MainEquipment eq = mainEq(1L);
        RentalCategory cat = mock(RentalCategory.class);
        when(mainEquipmentRepository.findById(1L)).thenReturn(Optional.of(eq));
        when(rentalCategoryRepository.findById(5L)).thenReturn(Optional.of(cat));
        when(mainEquipmentStatusRepository.findActiveByEquipmentIds(anyList(), any())).thenReturn(List.of());
        when(mainEquipmentStatusRepository.findUpcomingByEquipmentIds(anyList(), any())).thenReturn(List.of());
        when(mainEquipmentRepository.save(any())).thenReturn(eq);
        MainEquipmentRequest req = new MainEquipmentRequest(
                "Camera", null, "Canon", "R5", null, null, null, 5L, null);

        service.updateMainEquipment(1L, req);

        verify(rentalCategoryRepository).findById(5L);
        assertEquals(cat, eq.getPricingCategory());
    }

    @Test
    void updateMainEquipment_activeStatusPresent_usesItForCurrentStatus() {
        MainEquipment eq = mainEq(1L);
        MainEquipmentStatus ms = MainEquipmentStatus.builder()
                .mainEquipment(eq).statusType(MainEquipmentStatusType.BOOKED).build();
        when(mainEquipmentRepository.findById(1L)).thenReturn(Optional.of(eq));
        when(mainEquipmentStatusRepository.findActiveByEquipmentIds(anyList(), any()))
                .thenReturn(List.of(ms));
        when(mainEquipmentStatusRepository.findUpcomingByEquipmentIds(anyList(), any())).thenReturn(List.of());
        when(mainEquipmentRepository.save(any())).thenReturn(eq);
        MainEquipmentRequest req = new MainEquipmentRequest(
                "Camera", null, "Canon", "R5", null, null, null, null, null);

        MainEquipmentResponse response = service.updateMainEquipment(1L, req);

        assertEquals("BOOKED", response.status());
    }

    // ── deleteMainEquipment ───────────────────────────────────────────────────

    @Test
    void deleteMainEquipment_notFound_throwsNotFound() {
        when(mainEquipmentRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteMainEquipment(99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteMainEquipment_found_deletesEntity() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findById(1L)).thenReturn(Optional.of(eq));

        service.deleteMainEquipment(1L);

        verify(mainEquipmentRepository).delete(eq);
    }

    // ── addSubEquipment ───────────────────────────────────────────────────────

    @Test
    void addSubEquipment_nullPricingCategoryId_savesWithNullCategory() {
        SubEquipmentRequest req = new SubEquipmentRequest(
                "SD Card", "Storage", List.of(), "SanDisk", 64, 5, null, null, true);
        when(subEquipmentRepository.save(any())).thenReturn(subEq(1L));

        service.addSubEquipment(req);

        ArgumentCaptor<SubEquipment> cap = ArgumentCaptor.forClass(SubEquipment.class);
        verify(subEquipmentRepository).save(cap.capture());
        assertNull(cap.getValue().getPricingCategory());
    }

    @Test
    void addSubEquipment_isForRentNull_defaultsToFalse() {
        SubEquipmentRequest req = new SubEquipmentRequest(
                "SD Card", "Storage", null, "SanDisk", 64, 5, null, null, null);
        when(subEquipmentRepository.save(any())).thenReturn(subEq(1L));

        service.addSubEquipment(req);

        ArgumentCaptor<SubEquipment> cap = ArgumentCaptor.forClass(SubEquipment.class);
        verify(subEquipmentRepository).save(cap.capture());
        assertFalse(cap.getValue().isForRent());
    }

    @Test
    void addSubEquipment_withPricingCategoryId_attachesCategory() {
        RentalCategory cat = mock(RentalCategory.class);
        when(rentalCategoryRepository.findById(3L)).thenReturn(Optional.of(cat));
        SubEquipmentRequest req = new SubEquipmentRequest(
                "SD Card", "Storage", null, "SanDisk", 64, 5, null, 3L, null);
        when(subEquipmentRepository.save(any())).thenReturn(subEq(1L));

        service.addSubEquipment(req);

        ArgumentCaptor<SubEquipment> cap = ArgumentCaptor.forClass(SubEquipment.class);
        verify(subEquipmentRepository).save(cap.capture());
        assertEquals(cat, cap.getValue().getPricingCategory());
    }

    @Test
    void addSubEquipment_isForRentTrue_setsFlag() {
        SubEquipmentRequest req = new SubEquipmentRequest(
                "SD Card", "Storage", null, "SanDisk", 64, 5, null, null, true);
        when(subEquipmentRepository.save(any())).thenReturn(subEq(1L));

        service.addSubEquipment(req);

        ArgumentCaptor<SubEquipment> cap = ArgumentCaptor.forClass(SubEquipment.class);
        verify(subEquipmentRepository).save(cap.capture());
        assertTrue(cap.getValue().isForRent());
    }

    // ── updateSubEquipment ────────────────────────────────────────────────────

    @Test
    void updateSubEquipment_notFound_throwsNotFound() {
        when(subEquipmentRepository.findById(99L)).thenReturn(Optional.empty());
        SubEquipmentRequest req = new SubEquipmentRequest(
                null, "Storage", null, "SanDisk", 64, 5, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateSubEquipment(99L, req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateSubEquipment_nullType_doesNotOverrideExistingType() {
        SubEquipment sub = subEq(1L);
        sub.setType("OriginalType");
        when(subEquipmentRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.findUpcomingBySubEquipmentIds(anyList(), any()))
                .thenReturn(List.of());
        when(subEquipmentRepository.save(any())).thenReturn(sub);
        SubEquipmentRequest req = new SubEquipmentRequest(
                null, "Storage", null, "SanDisk", 64, 5, null, null, null);

        service.updateSubEquipment(1L, req);

        assertEquals("OriginalType", sub.getType());
    }

    @Test
    void updateSubEquipment_nonNullType_setsNewType() {
        SubEquipment sub = subEq(1L);
        when(subEquipmentRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.findUpcomingBySubEquipmentIds(anyList(), any()))
                .thenReturn(List.of());
        when(subEquipmentRepository.save(any())).thenReturn(sub);
        SubEquipmentRequest req = new SubEquipmentRequest(
                "SD Card", "Storage", null, "SanDisk", 64, 5, null, null, null);

        service.updateSubEquipment(1L, req);

        assertEquals("SD Card", sub.getType());
    }

    @Test
    void updateSubEquipment_nullPricingCategoryId_skipsResolution() {
        SubEquipment sub = subEq(1L);
        when(subEquipmentRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.findUpcomingBySubEquipmentIds(anyList(), any()))
                .thenReturn(List.of());
        when(subEquipmentRepository.save(any())).thenReturn(sub);
        SubEquipmentRequest req = new SubEquipmentRequest(
                null, "Storage", null, "SanDisk", 64, 5, null, null, null);

        service.updateSubEquipment(1L, req);

        verify(rentalCategoryRepository, never()).findById(any());
    }

    @Test
    void updateSubEquipment_withPricingCategoryId_resolvesAndSetsCategory() {
        SubEquipment sub = subEq(1L);
        RentalCategory cat = mock(RentalCategory.class);
        when(subEquipmentRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(rentalCategoryRepository.findById(7L)).thenReturn(Optional.of(cat));
        when(subEquipmentQuantityHoldRepository.findUpcomingBySubEquipmentIds(anyList(), any()))
                .thenReturn(List.of());
        when(subEquipmentRepository.save(any())).thenReturn(sub);
        SubEquipmentRequest req = new SubEquipmentRequest(
                "SD Card", "Storage", null, "SanDisk", 64, 5, null, 7L, null);

        service.updateSubEquipment(1L, req);

        verify(rentalCategoryRepository).findById(7L);
        assertEquals(cat, sub.getPricingCategory());
    }

    @Test
    void updateSubEquipment_withIsForRentTrue_setsFlag() {
        SubEquipment sub = subEq(1L);
        sub.setForRent(false);
        when(subEquipmentRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.findUpcomingBySubEquipmentIds(anyList(), any()))
                .thenReturn(List.of());
        when(subEquipmentRepository.save(any())).thenReturn(sub);
        SubEquipmentRequest req = new SubEquipmentRequest(
                null, "Storage", null, "SanDisk", 64, 5, null, null, true);

        service.updateSubEquipment(1L, req);

        assertTrue(sub.isForRent());
    }

    // ── deleteSubEquipment ────────────────────────────────────────────────────

    @Test
    void deleteSubEquipment_notFound_throwsNotFound() {
        when(subEquipmentRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteSubEquipment(99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteSubEquipment_found_deletes() {
        SubEquipment sub = subEq(1L);
        when(subEquipmentRepository.findById(1L)).thenReturn(Optional.of(sub));

        service.deleteSubEquipment(1L);

        verify(subEquipmentRepository).delete(sub);
    }

    // ── addMainEquipmentStatus ────────────────────────────────────────────────

    @Test
    void addMainEquipmentStatus_availableType_throwsBadRequest() {
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.AVAILABLE, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addMainEquipmentStatus(1L, req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(mainEquipmentRepository, never()).findById(any());
    }

    @Test
    void addMainEquipmentStatus_partiallyAvailableType_throwsBadRequest() {
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.PARTIALLY_AVAILABLE, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addMainEquipmentStatus(1L, req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void addMainEquipmentStatus_equipmentNotFound_throwsNotFound() {
        when(mainEquipmentRepository.findById(99L)).thenReturn(Optional.empty());
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.MAINTENANCE, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addMainEquipmentStatus(99L, req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void addMainEquipmentStatus_conflictingStatus_throwsConflict() {
        when(mainEquipmentRepository.findById(1L)).thenReturn(Optional.of(mainEq(1L)));
        when(mainEquipmentStatusRepository.existsConflictingStatus(eq(1L), any(), any()))
                .thenReturn(true);
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.MAINTENANCE, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addMainEquipmentStatus(1L, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void addMainEquipmentStatus_conflictingRentalBooking_throwsConflict() {
        when(mainEquipmentRepository.findById(1L)).thenReturn(Optional.of(mainEq(1L)));
        when(mainEquipmentStatusRepository.existsConflictingStatus(any(), any(), any())).thenReturn(false);
        when(equipmentRentalItemRepository.existsConflictingApprovedRental(
                anyLong(), any(), any(), anyLong(), anyList())).thenReturn(true);
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.MAINTENANCE, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addMainEquipmentStatus(1L, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void addMainEquipmentStatus_conflictingEventRequest_throwsConflict() {
        when(mainEquipmentRepository.findById(1L)).thenReturn(Optional.of(mainEq(1L)));
        when(mainEquipmentStatusRepository.existsConflictingStatus(any(), any(), any())).thenReturn(false);
        when(equipmentRentalItemRepository.existsConflictingApprovedRental(
                anyLong(), any(), any(), anyLong(), anyList())).thenReturn(false);
        when(eventEquipmentRequestItemRepository.existsConflictingRequest(
                anyLong(), anyLong(), anyList(), any(), any())).thenReturn(true);
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.MAINTENANCE, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addMainEquipmentStatus(1L, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void addMainEquipmentStatus_noConflicts_savesWithCorrectFields() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findById(1L)).thenReturn(Optional.of(eq));
        when(mainEquipmentStatusRepository.existsConflictingStatus(any(), any(), any())).thenReturn(false);
        when(equipmentRentalItemRepository.existsConflictingApprovedRental(
                anyLong(), any(), any(), anyLong(), anyList())).thenReturn(false);
        when(eventEquipmentRequestItemRepository.existsConflictingRequest(
                anyLong(), anyLong(), anyList(), any(), any())).thenReturn(false);
        MainEquipmentStatus saved = MainEquipmentStatus.builder()
                .id(1L).mainEquipment(eq).statusType(MainEquipmentStatusType.MAINTENANCE)
                .startDatetime(start).endDatetime(end).build();
        when(mainEquipmentStatusRepository.save(any())).thenReturn(saved);
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.MAINTENANCE, start, end, "notes");

        service.addMainEquipmentStatus(1L, req);

        verify(mainEquipmentStatusRepository).save(argThat(s ->
                s.getStatusType() == MainEquipmentStatusType.MAINTENANCE
                        && s.getMainEquipment().equals(eq)
                        && "notes".equals(s.getNotes())));
    }

    // ── getMainEquipmentStatuses ──────────────────────────────────────────────

    @Test
    void getMainEquipmentStatuses_equipmentNotFound_throwsNotFound() {
        when(mainEquipmentRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getMainEquipmentStatuses(99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getMainEquipmentStatuses_found_returnsMappedList() {
        when(mainEquipmentRepository.existsById(1L)).thenReturn(true);
        MainEquipment eq = mainEq(1L);
        MainEquipmentStatus ms = MainEquipmentStatus.builder()
                .id(10L).mainEquipment(eq).statusType(MainEquipmentStatusType.BOOKED)
                .startDatetime(start).endDatetime(end).build();
        when(mainEquipmentStatusRepository
                .findByMainEquipmentMainEquipmentIdOrderByStartDatetimeAsc(1L))
                .thenReturn(List.of(ms));

        List<MainEquipmentStatusResponse> result = service.getMainEquipmentStatuses(1L);

        assertEquals(1, result.size());
        assertEquals(MainEquipmentStatusType.BOOKED, result.get(0).statusType());
    }

    // ── updateMainEquipmentStatus ─────────────────────────────────────────────

    @Test
    void updateMainEquipmentStatus_availableType_throwsBadRequest() {
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.AVAILABLE, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateMainEquipmentStatus(1L, 10L, req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(mainEquipmentStatusRepository, never()).findById(any());
    }

    @Test
    void updateMainEquipmentStatus_partiallyAvailableType_throwsBadRequest() {
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.PARTIALLY_AVAILABLE, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateMainEquipmentStatus(1L, 10L, req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateMainEquipmentStatus_statusNotFound_throwsNotFound() {
        when(mainEquipmentStatusRepository.findById(10L)).thenReturn(Optional.empty());
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.MAINTENANCE, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateMainEquipmentStatus(1L, 10L, req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateMainEquipmentStatus_statusBelongsToDifferentEquipment_throwsNotFound() {
        MainEquipment otherEq = mainEq(99L);
        MainEquipmentStatus ms = MainEquipmentStatus.builder()
                .id(10L).mainEquipment(otherEq).statusType(MainEquipmentStatusType.MAINTENANCE)
                .startDatetime(start).endDatetime(end).build();
        when(mainEquipmentStatusRepository.findById(10L)).thenReturn(Optional.of(ms));
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.MAINTENANCE, start, end, null);

        // equipmentId=1 but status belongs to 99 → NOT_FOUND
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateMainEquipmentStatus(1L, 10L, req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateMainEquipmentStatus_conflictingStatus_throwsConflict() {
        MainEquipment eq = mainEq(1L);
        MainEquipmentStatus ms = MainEquipmentStatus.builder()
                .id(10L).mainEquipment(eq).statusType(MainEquipmentStatusType.MAINTENANCE)
                .startDatetime(start).endDatetime(end).build();
        when(mainEquipmentStatusRepository.findById(10L)).thenReturn(Optional.of(ms));
        when(mainEquipmentStatusRepository.existsConflictingStatusExcluding(
                eq(1L), eq(10L), any(), any())).thenReturn(true);
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.MAINTENANCE, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateMainEquipmentStatus(1L, 10L, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateMainEquipmentStatus_noConflicts_updatesAllFieldsAndSaves() {
        MainEquipment eq = mainEq(1L);
        MainEquipmentStatus ms = MainEquipmentStatus.builder()
                .id(10L).mainEquipment(eq).statusType(MainEquipmentStatusType.MAINTENANCE)
                .startDatetime(start).endDatetime(end).build();
        when(mainEquipmentStatusRepository.findById(10L)).thenReturn(Optional.of(ms));
        when(mainEquipmentStatusRepository.existsConflictingStatusExcluding(any(), any(), any(), any()))
                .thenReturn(false);
        when(equipmentRentalItemRepository.existsConflictingApprovedRental(
                anyLong(), any(), any(), anyLong(), anyList())).thenReturn(false);
        when(eventEquipmentRequestItemRepository.existsConflictingRequest(
                anyLong(), anyLong(), anyList(), any(), any())).thenReturn(false);
        when(mainEquipmentStatusRepository.save(any())).thenReturn(ms);
        LocalDateTime newStart = start.plusHours(2);
        LocalDateTime newEnd   = end.plusHours(2);
        MainEquipmentStatusRequest req = new MainEquipmentStatusRequest(
                MainEquipmentStatusType.BOOKED, newStart, newEnd, "updated");

        service.updateMainEquipmentStatus(1L, 10L, req);

        assertEquals(MainEquipmentStatusType.BOOKED, ms.getStatusType());
        assertEquals(newStart, ms.getStartDatetime());
        assertEquals(newEnd, ms.getEndDatetime());
        assertEquals("updated", ms.getNotes());
        verify(mainEquipmentStatusRepository).save(ms);
    }

    // ── deleteMainEquipmentStatus ─────────────────────────────────────────────

    @Test
    void deleteMainEquipmentStatus_notFound_throwsNotFound() {
        when(mainEquipmentStatusRepository.findById(10L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteMainEquipmentStatus(1L, 10L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteMainEquipmentStatus_statusBelongsToDifferentEquipment_throwsNotFound() {
        MainEquipment otherEq = mainEq(99L);
        MainEquipmentStatus ms = MainEquipmentStatus.builder()
                .id(10L).mainEquipment(otherEq).startDatetime(start).endDatetime(end).build();
        when(mainEquipmentStatusRepository.findById(10L)).thenReturn(Optional.of(ms));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteMainEquipmentStatus(1L, 10L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteMainEquipmentStatus_found_deletes() {
        MainEquipment eq = mainEq(1L);
        MainEquipmentStatus ms = MainEquipmentStatus.builder()
                .id(10L).mainEquipment(eq).startDatetime(start).endDatetime(end).build();
        when(mainEquipmentStatusRepository.findById(10L)).thenReturn(Optional.of(ms));

        service.deleteMainEquipmentStatus(1L, 10L);

        verify(mainEquipmentStatusRepository).delete(ms);
    }

    // ── addQuantityHold ───────────────────────────────────────────────────────

    @Test
    void addQuantityHold_subNotFound_throwsNotFound() {
        when(subEquipmentRepository.findById(99L)).thenReturn(Optional.empty());
        SubEquipmentQuantityHoldRequest req = new SubEquipmentQuantityHoldRequest(
                2, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addQuantityHold(99L, req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void addQuantityHold_totalAllocatedPlusRequestExceedsCapacity_throwsConflict() {
        SubEquipment sub = subEq(1L); // totalQuantity=5
        when(subEquipmentRepository.findById(1L)).thenReturn(Optional.of(sub));
        // holds=2, rental=2, event=1 → totalAllocated=5; 5+2=7 > 5 → CONFLICT
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(anyLong(), any(), any(), anyLong()))
                .thenReturn(2);
        when(equipmentRentalSubItemRepository.sumCommittedQuantity(anyLong(), any(), any(), anyList()))
                .thenReturn(2);
        when(eventEquipmentRequestSubItemRepository.sumCommittedQuantity(
                anyLong(), any(), any(), anyList(), anyLong())).thenReturn(1);
        SubEquipmentQuantityHoldRequest req = new SubEquipmentQuantityHoldRequest(
                2, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.addQuantityHold(1L, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void addQuantityHold_withinCapacity_savesHoldWithCorrectFields() {
        SubEquipment sub = subEq(1L); // totalQuantity=5
        when(subEquipmentRepository.findById(1L)).thenReturn(Optional.of(sub));
        // holds=1, rental=1, event=1 → totalAllocated=3; 3+1=4 <= 5 → OK
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(anyLong(), any(), any(), anyLong()))
                .thenReturn(1);
        when(equipmentRentalSubItemRepository.sumCommittedQuantity(anyLong(), any(), any(), anyList()))
                .thenReturn(1);
        when(eventEquipmentRequestSubItemRepository.sumCommittedQuantity(
                anyLong(), any(), any(), anyList(), anyLong())).thenReturn(1);
        SubEquipmentQuantityHold saved = SubEquipmentQuantityHold.builder()
                .id(1L).subEquipment(sub).quantity(1).startDatetime(start).endDatetime(end).build();
        when(subEquipmentQuantityHoldRepository.save(any())).thenReturn(saved);
        SubEquipmentQuantityHoldRequest req = new SubEquipmentQuantityHoldRequest(
                1, start, end, "note");

        service.addQuantityHold(1L, req);

        verify(subEquipmentQuantityHoldRepository).save(argThat(h ->
                h.getQuantity() == 1 && h.getSubEquipment().equals(sub)
                        && "note".equals(h.getNotes())));
    }

    // ── getQuantityHolds ──────────────────────────────────────────────────────

    @Test
    void getQuantityHolds_subNotFound_throwsNotFound() {
        when(subEquipmentRepository.existsById(99L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getQuantityHolds(99L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getQuantityHolds_found_returnsMappedList() {
        SubEquipment sub = subEq(1L);
        when(subEquipmentRepository.existsById(1L)).thenReturn(true);
        SubEquipmentQuantityHold hold = SubEquipmentQuantityHold.builder()
                .id(5L).subEquipment(sub).quantity(2).startDatetime(start).endDatetime(end).build();
        when(subEquipmentQuantityHoldRepository
                .findBySubEquipmentSubEquipmentIdOrderByStartDatetimeAsc(1L))
                .thenReturn(List.of(hold));

        List<SubEquipmentQuantityHoldResponse> result = service.getQuantityHolds(1L);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).quantity());
    }

    // ── updateQuantityHold ────────────────────────────────────────────────────

    @Test
    void updateQuantityHold_holdNotFound_throwsNotFound() {
        when(subEquipmentQuantityHoldRepository.findById(10L)).thenReturn(Optional.empty());
        SubEquipmentQuantityHoldRequest req = new SubEquipmentQuantityHoldRequest(
                1, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateQuantityHold(1L, 10L, req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateQuantityHold_holdBelongsToDifferentSub_throwsNotFound() {
        SubEquipment otherSub = subEq(99L);
        SubEquipmentQuantityHold hold = SubEquipmentQuantityHold.builder()
                .id(10L).subEquipment(otherSub).quantity(1).startDatetime(start).endDatetime(end).build();
        when(subEquipmentQuantityHoldRepository.findById(10L)).thenReturn(Optional.of(hold));
        SubEquipmentQuantityHoldRequest req = new SubEquipmentQuantityHoldRequest(
                1, start, end, null);

        // subEquipmentId=1 but hold belongs to 99 → NOT_FOUND
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateQuantityHold(1L, 10L, req));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void updateQuantityHold_exceedsCapacity_throwsConflict() {
        SubEquipment sub = subEq(1L); // totalQuantity=5
        SubEquipmentQuantityHold hold = SubEquipmentQuantityHold.builder()
                .id(10L).subEquipment(sub).quantity(1).startDatetime(start).endDatetime(end).build();
        when(subEquipmentQuantityHoldRepository.findById(10L)).thenReturn(Optional.of(hold));
        // excluding holdId=10: existing holds=3, rental=1, event=1 → totalAllocated=5; 5+2=7 > 5
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(anyLong(), any(), any(), eq(10L)))
                .thenReturn(3);
        when(equipmentRentalSubItemRepository.sumCommittedQuantity(anyLong(), any(), any(), anyList()))
                .thenReturn(1);
        when(eventEquipmentRequestSubItemRepository.sumCommittedQuantity(
                anyLong(), any(), any(), anyList(), anyLong())).thenReturn(1);
        SubEquipmentQuantityHoldRequest req = new SubEquipmentQuantityHoldRequest(
                2, start, end, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateQuantityHold(1L, 10L, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void updateQuantityHold_withinCapacity_updatesFieldsAndSaves() {
        SubEquipment sub = subEq(1L); // totalQuantity=5
        SubEquipmentQuantityHold hold = SubEquipmentQuantityHold.builder()
                .id(10L).subEquipment(sub).quantity(1).startDatetime(start).endDatetime(end).build();
        when(subEquipmentQuantityHoldRepository.findById(10L)).thenReturn(Optional.of(hold));
        // totalAllocated=3; 3+2=5 <= 5 → OK
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(anyLong(), any(), any(), eq(10L)))
                .thenReturn(1);
        when(equipmentRentalSubItemRepository.sumCommittedQuantity(anyLong(), any(), any(), anyList()))
                .thenReturn(1);
        when(eventEquipmentRequestSubItemRepository.sumCommittedQuantity(
                anyLong(), any(), any(), anyList(), anyLong())).thenReturn(1);
        when(subEquipmentQuantityHoldRepository.save(any())).thenReturn(hold);
        LocalDateTime newStart = start.plusHours(4);
        LocalDateTime newEnd   = end.plusHours(4);
        SubEquipmentQuantityHoldRequest req = new SubEquipmentQuantityHoldRequest(
                2, newStart, newEnd, "updated");

        service.updateQuantityHold(1L, 10L, req);

        assertEquals(2, hold.getQuantity());
        assertEquals(newStart, hold.getStartDatetime());
        assertEquals(newEnd, hold.getEndDatetime());
        assertEquals("updated", hold.getNotes());
        verify(subEquipmentQuantityHoldRepository).save(hold);
    }

    // ── deleteQuantityHold ────────────────────────────────────────────────────

    @Test
    void deleteQuantityHold_notFound_throwsNotFound() {
        when(subEquipmentQuantityHoldRepository.findById(10L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteQuantityHold(1L, 10L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteQuantityHold_holdBelongsToDifferentSub_throwsNotFound() {
        SubEquipment otherSub = subEq(99L);
        SubEquipmentQuantityHold hold = SubEquipmentQuantityHold.builder()
                .id(10L).subEquipment(otherSub).quantity(1).startDatetime(start).endDatetime(end).build();
        when(subEquipmentQuantityHoldRepository.findById(10L)).thenReturn(Optional.of(hold));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.deleteQuantityHold(1L, 10L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteQuantityHold_found_deletes() {
        SubEquipment sub = subEq(1L);
        SubEquipmentQuantityHold hold = SubEquipmentQuantityHold.builder()
                .id(10L).subEquipment(sub).quantity(1).startDatetime(start).endDatetime(end).build();
        when(subEquipmentQuantityHoldRepository.findById(10L)).thenReturn(Optional.of(hold));

        service.deleteQuantityHold(1L, 10L);

        verify(subEquipmentQuantityHoldRepository).delete(hold);
    }

    // ── getAvailableEquipment — context branching ─────────────────────────────

    @Test
    void getAvailableEquipment_rentalContext_usesForRentTrueQueryForBothMainAndSub() {
        when(mainEquipmentRepository.findAvailableEquipment(
                eq(true), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentRepository.findByIsForRentTrue()).thenReturn(List.of());

        service.getAvailableEquipment(startDate, endDate, "RENTAL");

        verify(mainEquipmentRepository).findByIsForRentTrue();
        verify(subEquipmentRepository).findByIsForRentTrue();
        verify(mainEquipmentRepository, never()).findAll();
        verify(subEquipmentRepository, never()).findAll();
    }

    @Test
    void getAvailableEquipment_nonRentalContext_usesAllEquipmentQuery() {
        when(mainEquipmentRepository.findAvailableEquipment(
                eq(false), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentRepository.findAll()).thenReturn(List.of());

        service.getAvailableEquipment(startDate, endDate, "EVENT");

        verify(mainEquipmentRepository).findAll();
        verify(subEquipmentRepository).findAll();
        verify(mainEquipmentRepository, never()).findByIsForRentTrue();
    }

    @Test
    void getAvailableEquipment_rentalContextCaseInsensitive_treatsAsRental() {
        when(mainEquipmentRepository.findAvailableEquipment(
                eq(true), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentRepository.findByIsForRentTrue()).thenReturn(List.of());

        service.getAvailableEquipment(startDate, endDate, "rental");

        verify(mainEquipmentRepository).findByIsForRentTrue();
    }

    @Test
    void getAvailableEquipment_someEquipmentNotFullyAvailable_analyzesPartialCandidates_returnsPartiallyAvailable() {
        MainEquipment eq1 = mainEq(1L);
        MainEquipment eq2 = mainEq(2L);
        // eq1 fully available; eq2 is a partial candidate (not in fullyAvailableIds)
        when(mainEquipmentRepository.findAvailableEquipment(
                eq(true), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of(eq1));
        when(mainEquipmentRepository.findByIsForRentTrue()).thenReturn(List.of(eq1, eq2));
        // eq2 has a start-boundary rental conflict → analyzePartialCandidates → PARTIALLY_AVAILABLE
        LocalDateTime returnDt = startDate.atTime(0, 0);
        when(equipmentRentalItemRepository.findRentalConflictsForEquipment(
                anyList(), any(), any(), anyList(), anyLong()))
                .thenReturn(List.of(new RentalConflictRow(2L, 10L, returnDt, null,
                        startDate.minusDays(2), startDate)));
        when(mainEquipmentStatusRepository.findStatusWindowConflictsForEquipment(anyList(), any(), any()))
                .thenReturn(List.of());
        when(eventEquipmentRequestItemRepository.findEventConflictsForEquipment(anyList(), any(), any(), anyList()))
                .thenReturn(List.of());

        EquipmentListResponse result = service.getAvailableEquipment(startDate, endDate, "RENTAL");

        List<MainEquipmentResponse> mainList = result.mainEquipment();
        assertEquals(2, mainList.size());
        assertTrue(mainList.stream().anyMatch(r -> "AVAILABLE".equals(r.status())));
        assertTrue(mainList.stream().anyMatch(r -> "PARTIALLY_AVAILABLE".equals(r.status())));
    }

    // ── getAllEquipment(LocalDate, LocalDate) — main equipment status computation ─

    private void stubRentalConflicts(RentalConflictRow... rows) {
        when(equipmentRentalItemRepository.findRentalConflictsForEquipment(
                anyList(), any(), any(), anyList(), anyLong()))
                .thenReturn(List.of(rows));
        when(mainEquipmentStatusRepository.findStatusWindowConflictsForEquipment(anyList(), any(), any()))
                .thenReturn(List.of());
        when(eventEquipmentRequestItemRepository.findEventConflictsForEquipment(anyList(), any(), any(), anyList()))
                .thenReturn(List.of());
    }

    @Test
    void getAllEquipment_withDates_fullyAvailable_returnsAvailable_skipsPartialAnalysis() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of(eq));
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());

        EquipmentListResponse result = service.getAllEquipment(startDate, endDate);

        assertEquals("AVAILABLE", result.mainEquipment().get(0).status());
        verify(equipmentRentalItemRepository, never())
                .findRentalConflictsForEquipment(any(), any(), any(), any(), anyLong());
    }

    @Test
    void getAllEquipment_withDates_interiorRentalConflict_returnsBooked() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        // progStart < startDate and progEnd > endDate → neither effEnd==startDate nor effStart==endDate
        stubRentalConflicts(new RentalConflictRow(1L, 10L, null, null,
                startDate.minusDays(1), endDate.plusDays(1)));

        assertEquals("BOOKED", service.getAllEquipment(startDate, endDate).mainEquipment().get(0).status());
    }

    @Test
    void getAllEquipment_withDates_startBoundaryRentalConflict_returnsPartiallyAvailable() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        // returnDt.toLocalDate() == startDate → isStartBoundary=true
        // availableAfter = startDate.atTime(1,0) → date == startDate → not impossible
        LocalDateTime returnDt = startDate.atTime(0, 0);
        stubRentalConflicts(new RentalConflictRow(1L, 10L, returnDt, null,
                startDate.minusDays(2), startDate));

        MainEquipmentResponse resp = service.getAllEquipment(startDate, endDate).mainEquipment().get(0);
        assertEquals("PARTIALLY_AVAILABLE", resp.status());
        assertEquals(1, resp.boundaryNotes().size());
        assertNotNull(resp.boundaryNotes().get(0).availableAfter());
        assertNull(resp.boundaryNotes().get(0).mustReturnBefore());
    }

    @Test
    void getAllEquipment_withDates_endBoundaryRentalConflict_returnsPartiallyAvailable() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        // pickupDt.toLocalDate() == endDate → isEndBoundary=true
        // mustReturnBefore = endDate.atTime(9,0) → date == endDate → not impossible
        LocalDateTime pickupDt = endDate.atTime(10, 0);
        stubRentalConflicts(new RentalConflictRow(1L, 10L, null, pickupDt, endDate, endDate.plusDays(2)));

        MainEquipmentResponse resp = service.getAllEquipment(startDate, endDate).mainEquipment().get(0);
        assertEquals("PARTIALLY_AVAILABLE", resp.status());
        assertNull(resp.boundaryNotes().get(0).availableAfter());
        assertNotNull(resp.boundaryNotes().get(0).mustReturnBefore());
    }

    @Test
    void getAllEquipment_withDates_availableAfterLandsNextDay_returnsBooked() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        // returnDt=startDate.atTime(23,0) → availableAfter = startDate+1.atTime(0,0) > startDate → impossible
        LocalDateTime returnDt = startDate.atTime(23, 0);
        stubRentalConflicts(new RentalConflictRow(1L, 10L, returnDt, null,
                startDate.minusDays(1), startDate));

        assertEquals("BOOKED", service.getAllEquipment(startDate, endDate).mainEquipment().get(0).status());
    }

    @Test
    void getAllEquipment_withDates_sameDayRange_withBothBoundaries_returnsBooked() {
        LocalDate sameDay = LocalDate.now().plusDays(7);
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        // Two distinct rentals: one ends on sameDay (startBoundary), one starts on sameDay (endBoundary)
        // startDate==endDate && hasAfter && hasBefore → BOOKED
        stubRentalConflicts(
                new RentalConflictRow(1L, 20L, sameDay.atTime(0, 0), null, sameDay.minusDays(2), sameDay),
                new RentalConflictRow(1L, 21L, null, sameDay.atTime(10, 0), sameDay, sameDay.plusDays(2)));

        assertEquals("BOOKED", service.getAllEquipment(sameDay, sameDay).mainEquipment().get(0).status());
    }

    @Test
    void getAllEquipment_withDates_interiorStatusConflict_returnsBooked() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        when(equipmentRentalItemRepository.findRentalConflictsForEquipment(
                anyList(), any(), any(), anyList(), anyLong()))
                .thenReturn(List.of());
        // sEnd != startDate and sStart != endDate → !isStartBoundary && !isEndBoundary → BOOKED
        LocalDateTime sStart = startDate.plusDays(1).atTime(8, 0);
        LocalDateTime sEnd   = endDate.minusDays(1).atTime(18, 0);
        when(mainEquipmentStatusRepository.findStatusWindowConflictsForEquipment(anyList(), any(), any()))
                .thenReturn(List.of(new StatusConflictRow(1L, 50L, sStart, sEnd)));
        when(eventEquipmentRequestItemRepository.findEventConflictsForEquipment(anyList(), any(), any(), anyList()))
                .thenReturn(List.of());

        assertEquals("BOOKED", service.getAllEquipment(startDate, endDate).mainEquipment().get(0).status());
    }

    @Test
    void getAllEquipment_withDates_endBoundaryStatusConflict_returnsPartiallyAvailable() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        when(equipmentRentalItemRepository.findRentalConflictsForEquipment(
                anyList(), any(), any(), anyList(), anyLong()))
                .thenReturn(List.of());
        // sStart.toLocalDate() == endDate → isEndBoundary=true; mustReturnBefore on endDate → not impossible
        LocalDateTime sStart = endDate.atTime(2, 0);
        LocalDateTime sEnd   = endDate.plusDays(2).atTime(18, 0);
        when(mainEquipmentStatusRepository.findStatusWindowConflictsForEquipment(anyList(), any(), any()))
                .thenReturn(List.of(new StatusConflictRow(1L, 55L, sStart, sEnd)));
        when(eventEquipmentRequestItemRepository.findEventConflictsForEquipment(anyList(), any(), any(), anyList()))
                .thenReturn(List.of());

        MainEquipmentResponse resp = service.getAllEquipment(startDate, endDate).mainEquipment().get(0);
        assertEquals("PARTIALLY_AVAILABLE", resp.status());
        assertEquals(1, resp.boundaryNotes().size());
        assertNotNull(resp.boundaryNotes().get(0).mustReturnBefore());
        assertNull(resp.boundaryNotes().get(0).availableAfter());
        assertEquals(55L, resp.boundaryNotes().get(0).statusId());
    }

    @Test
    void getAllEquipment_withDates_interiorEventConflict_returnsBooked() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        when(equipmentRentalItemRepository.findRentalConflictsForEquipment(
                anyList(), any(), any(), anyList(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusWindowConflictsForEquipment(anyList(), any(), any()))
                .thenReturn(List.of());
        // effEnd != startDate and effStart != endDate → !isStartBoundary && !isEndBoundary → BOOKED
        LocalDateTime effEndDt   = startDate.plusDays(1).atTime(18, 0);
        LocalDateTime effStartDt = startDate.plusDays(1).atTime(8, 0);
        when(eventEquipmentRequestItemRepository.findEventConflictsForEquipment(anyList(), any(), any(), anyList()))
                .thenReturn(List.of(new EventConflictRow(1L, 99L, effEndDt, effStartDt)));

        assertEquals("BOOKED", service.getAllEquipment(startDate, endDate).mainEquipment().get(0).status());
    }

    @Test
    void getAllEquipment_withDates_startBoundaryEventConflict_returnsPartiallyAvailable() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        when(equipmentRentalItemRepository.findRentalConflictsForEquipment(
                anyList(), any(), any(), anyList(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusWindowConflictsForEquipment(anyList(), any(), any()))
                .thenReturn(List.of());
        // effEndDt.toLocalDate() == startDate → isStartBoundary=true; availableAfter on startDate → not impossible
        LocalDateTime effEndDt   = startDate.atTime(0, 0);
        LocalDateTime effStartDt = startDate.minusDays(2).atTime(8, 0);
        when(eventEquipmentRequestItemRepository.findEventConflictsForEquipment(anyList(), any(), any(), anyList()))
                .thenReturn(List.of(new EventConflictRow(1L, 77L, effEndDt, effStartDt)));

        MainEquipmentResponse resp = service.getAllEquipment(startDate, endDate).mainEquipment().get(0);
        assertEquals("PARTIALLY_AVAILABLE", resp.status());
        assertEquals(1, resp.boundaryNotes().size());
        assertNotNull(resp.boundaryNotes().get(0).availableAfter());
        assertNull(resp.boundaryNotes().get(0).mustReturnBefore());
        assertEquals(77L, resp.boundaryNotes().get(0).eventRequestId());
    }

    // ── mergeBoundaryNotes ────────────────────────────────────────────────────

    @Test
    void mergeBoundaryNotes_sameRentalId_twoRows_mergedIntoOneNote() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        // Both rows carry rentalId=10L; row1 → availableAfter, row2 → mustReturnBefore
        // After mergeBoundaryNotes: 1 note with both fields set
        stubRentalConflicts(
                new RentalConflictRow(1L, 10L, startDate.atTime(0, 0), null, startDate.minusDays(2), startDate),
                new RentalConflictRow(1L, 10L, null, endDate.atTime(10, 0), endDate, endDate.plusDays(2)));

        MainEquipmentResponse resp = service.getAllEquipment(startDate, endDate).mainEquipment().get(0);
        assertEquals("PARTIALLY_AVAILABLE", resp.status());
        assertEquals(1, resp.boundaryNotes().size());
        assertEquals(10L, resp.boundaryNotes().get(0).rentalId());
        assertNotNull(resp.boundaryNotes().get(0).availableAfter());
        assertNotNull(resp.boundaryNotes().get(0).mustReturnBefore());
    }

    @Test
    void mergeBoundaryNotes_statusConflict_startBoundary_producesStatusNote() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        when(equipmentRentalItemRepository.findRentalConflictsForEquipment(
                anyList(), any(), any(), anyList(), anyLong()))
                .thenReturn(List.of());
        // sEnd.toLocalDate() == startDate → isStartBoundary=true; availableAfter on startDate → not impossible
        LocalDateTime sEnd = startDate.atTime(0, 0);
        when(mainEquipmentStatusRepository.findStatusWindowConflictsForEquipment(anyList(), any(), any()))
                .thenReturn(List.of(new StatusConflictRow(1L, 50L, startDate.minusDays(2).atTime(8, 0), sEnd)));
        when(eventEquipmentRequestItemRepository.findEventConflictsForEquipment(
                anyList(), any(), any(), anyList()))
                .thenReturn(List.of());

        MainEquipmentResponse resp = service.getAllEquipment(startDate, endDate).mainEquipment().get(0);
        assertEquals("PARTIALLY_AVAILABLE", resp.status());
        assertEquals(1, resp.boundaryNotes().size());
        assertEquals(50L, resp.boundaryNotes().get(0).statusId());
        assertNotNull(resp.boundaryNotes().get(0).availableAfter());
        assertNull(resp.boundaryNotes().get(0).rentalId());
    }

    @Test
    void mergeBoundaryNotes_eventRequestConflict_endBoundary_producesEventNote() {
        MainEquipment eq = mainEq(1L);
        when(mainEquipmentRepository.findAll()).thenReturn(List.of(eq));
        when(mainEquipmentRepository.findAvailableEquipment(
                anyBoolean(), any(), any(), anyList(), anyList(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusTypesForRange(anyList(), any(), any()))
                .thenReturn(List.of());
        when(equipmentRentalItemRepository.findRentalConflictsForEquipment(
                anyList(), any(), any(), anyList(), anyLong()))
                .thenReturn(List.of());
        when(mainEquipmentStatusRepository.findStatusWindowConflictsForEquipment(anyList(), any(), any()))
                .thenReturn(List.of());
        // effStartDt.toLocalDate() == endDate → isEndBoundary=true; mustReturnBefore on endDate → not impossible
        LocalDateTime effStartDt = endDate.atTime(10, 0);
        when(eventEquipmentRequestItemRepository.findEventConflictsForEquipment(
                anyList(), any(), any(), anyList()))
                .thenReturn(List.of(new EventConflictRow(1L, 99L,
                        endDate.plusDays(2).atTime(10, 0), effStartDt)));

        MainEquipmentResponse resp = service.getAllEquipment(startDate, endDate).mainEquipment().get(0);
        assertEquals("PARTIALLY_AVAILABLE", resp.status());
        assertEquals(1, resp.boundaryNotes().size());
        assertEquals(99L, resp.boundaryNotes().get(0).eventRequestId());
        assertNotNull(resp.boundaryNotes().get(0).mustReturnBefore());
        assertNull(resp.boundaryNotes().get(0).rentalId());
    }

    // ── filterValidSubBoundaryNotes ───────────────────────────────────────────

    @Test
    void filterValidSubBoundaryNotes_availableAfterOnStartDate_noteIsKept() {
        when(mainEquipmentRepository.findAll()).thenReturn(List.of());
        when(subEquipmentRepository.findAll()).thenReturn(List.of(subEq(1L)));
        stubEmptySubBoundaries();
        // returnDt = startDate-1.atTime(23,0) → availableAfter = startDate.atTime(0,0) → date == startDate → kept
        when(equipmentRentalSubItemRepository.findBoundaryReturnQuantities(anyList(), any(), anyList(), anyLong()))
                .thenReturn(List.of(new SubBoundaryRow(1L, 30L, startDate.minusDays(1).atTime(23, 0), 2L)));

        SubEquipmentResponse resp = service.getAllEquipment(startDate, endDate).subEquipment().get(0);
        assertEquals(1, resp.boundaryNotes().size());
    }

    @Test
    void filterValidSubBoundaryNotes_availableAfterAfterStartDate_noteIsFiltered() {
        when(mainEquipmentRepository.findAll()).thenReturn(List.of());
        when(subEquipmentRepository.findAll()).thenReturn(List.of(subEq(1L)));
        stubEmptySubBoundaries();
        // returnDt = startDate.atTime(23,0) → availableAfter = startDate+1.atTime(0,0) > startDate → filtered
        when(equipmentRentalSubItemRepository.findBoundaryReturnQuantities(anyList(), any(), anyList(), anyLong()))
                .thenReturn(List.of(new SubBoundaryRow(1L, 30L, startDate.atTime(23, 0), 2L)));

        SubEquipmentResponse resp = service.getAllEquipment(startDate, endDate).subEquipment().get(0);
        assertTrue(resp.boundaryNotes().isEmpty());
    }

    @Test
    void filterValidSubBoundaryNotes_mustReturnBeforeAfterEndDate_noteIsKept() {
        when(mainEquipmentRepository.findAll()).thenReturn(List.of());
        when(subEquipmentRepository.findAll()).thenReturn(List.of(subEq(1L)));
        stubEmptySubBoundaries();
        // pickupDt = endDate+1.atTime(1,0) → mustReturnBefore = endDate+1.atTime(0,0) → date > endDate → kept
        when(equipmentRentalSubItemRepository.findBoundaryPickupQuantities(anyList(), any(), anyList(), anyLong()))
                .thenReturn(List.of(new SubBoundaryRow(1L, 31L, endDate.plusDays(1).atTime(1, 0), 3L)));

        SubEquipmentResponse resp = service.getAllEquipment(startDate, endDate).subEquipment().get(0);
        assertEquals(1, resp.boundaryNotes().size());
    }

    @Test
    void filterValidSubBoundaryNotes_mustReturnBeforeBeforeEndDate_noteIsFiltered() {
        when(mainEquipmentRepository.findAll()).thenReturn(List.of());
        when(subEquipmentRepository.findAll()).thenReturn(List.of(subEq(1L)));
        stubEmptySubBoundaries();
        // pickupDt = endDate-1.atTime(1,0) → mustReturnBefore = endDate-1.atTime(0,0) < endDate → filtered
        when(equipmentRentalSubItemRepository.findBoundaryPickupQuantities(anyList(), any(), anyList(), anyLong()))
                .thenReturn(List.of(new SubBoundaryRow(1L, 31L, endDate.minusDays(1).atTime(1, 0), 3L)));

        SubEquipmentResponse resp = service.getAllEquipment(startDate, endDate).subEquipment().get(0);
        assertTrue(resp.boundaryNotes().isEmpty());
    }

    @Test
    void filterValidSubBoundaryNotes_nullMustReturnBefore_conditionAlwaysSatisfied() {
        when(mainEquipmentRepository.findAll()).thenReturn(List.of());
        when(subEquipmentRepository.findAll()).thenReturn(List.of(subEq(1L)));
        stubEmptySubBoundaries();
        // Hold-end note: availableAfter set, mustReturnBefore=null → null branch is always true → kept
        when(subEquipmentQuantityHoldRepository.findBoundaryEndQuantities(anyList(), any()))
                .thenReturn(List.of(new SubBoundaryRow(1L, 40L, startDate.minusDays(1).atTime(23, 0), 1L)));

        SubEquipmentResponse resp = service.getAllEquipment(startDate, endDate).subEquipment().get(0);
        assertEquals(1, resp.boundaryNotes().size());
        assertNull(resp.boundaryNotes().get(0).mustReturnBefore());
    }

    // ── mergeSubBoundaryNotes ─────────────────────────────────────────────────

    @Test
    void mergeSubBoundaryNotes_sameRentalId_maxQuantityRetained() {
        when(mainEquipmentRepository.findAll()).thenReturn(List.of());
        when(subEquipmentRepository.findAll()).thenReturn(List.of(subEq(1L)));
        stubEmptySubBoundaries();
        // Two rows with same rentalId, different quantities → max(3, 7) = 7 after merge
        LocalDateTime returnDt = startDate.minusDays(1).atTime(23, 0);
        when(equipmentRentalSubItemRepository.findBoundaryReturnQuantities(anyList(), any(), anyList(), anyLong()))
                .thenReturn(List.of(
                        new SubBoundaryRow(1L, 20L, returnDt, 3L),
                        new SubBoundaryRow(1L, 20L, returnDt, 7L)));

        SubEquipmentResponse resp = service.getAllEquipment(startDate, endDate).subEquipment().get(0);
        assertEquals(1, resp.boundaryNotes().size());
        assertEquals(7, resp.boundaryNotes().get(0).quantity());
        assertEquals(20L, resp.boundaryNotes().get(0).rentalId());
    }

    @Test
    void mergeSubBoundaryNotes_sameRentalId_availableAfterAndMustReturnBefore_mergedIntoOneNote() {
        when(mainEquipmentRepository.findAll()).thenReturn(List.of());
        when(subEquipmentRepository.findAll()).thenReturn(List.of(subEq(1L)));
        stubEmptySubBoundaries();
        // Return note (availableAfter) + pickup note (mustReturnBefore) for same rentalId → merged
        when(equipmentRentalSubItemRepository.findBoundaryReturnQuantities(anyList(), any(), anyList(), anyLong()))
                .thenReturn(List.of(new SubBoundaryRow(1L, 20L, startDate.minusDays(1).atTime(23, 0), 2L)));
        when(equipmentRentalSubItemRepository.findBoundaryPickupQuantities(anyList(), any(), anyList(), anyLong()))
                .thenReturn(List.of(new SubBoundaryRow(1L, 20L, endDate.plusDays(1).atTime(1, 0), 2L)));

        SubEquipmentResponse resp = service.getAllEquipment(startDate, endDate).subEquipment().get(0);
        assertEquals(1, resp.boundaryNotes().size());
        assertNotNull(resp.boundaryNotes().get(0).availableAfter());
        assertNotNull(resp.boundaryNotes().get(0).mustReturnBefore());
        assertEquals(20L, resp.boundaryNotes().get(0).rentalId());
    }

    @Test
    void mergeSubBoundaryNotes_sameHoldId_mergedIntoOneNote() {
        when(mainEquipmentRepository.findAll()).thenReturn(List.of());
        when(subEquipmentRepository.findAll()).thenReturn(List.of(subEq(1L)));
        stubEmptySubBoundaries();
        // Hold-end (availableAfter) + hold-start (mustReturnBefore) for same holdId=40 → merged
        when(subEquipmentQuantityHoldRepository.findBoundaryEndQuantities(anyList(), any()))
                .thenReturn(List.of(new SubBoundaryRow(1L, 40L, startDate.minusDays(1).atTime(23, 0), 1L)));
        when(subEquipmentQuantityHoldRepository.findBoundaryStartQuantities(anyList(), any()))
                .thenReturn(List.of(new SubBoundaryRow(1L, 40L, endDate.plusDays(1).atTime(1, 0), 1L)));

        SubEquipmentResponse resp = service.getAllEquipment(startDate, endDate).subEquipment().get(0);
        assertEquals(1, resp.boundaryNotes().size());
        assertNotNull(resp.boundaryNotes().get(0).availableAfter());
        assertNotNull(resp.boundaryNotes().get(0).mustReturnBefore());
        assertEquals(40L, resp.boundaryNotes().get(0).holdId());
        assertNull(resp.boundaryNotes().get(0).rentalId());
    }
}
