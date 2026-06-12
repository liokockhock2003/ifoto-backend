package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.model.*;
import com.ifoto.ifoto_backend.model.enumerator.DocumentType;
import com.ifoto.ifoto_backend.repository.EquipmentRentalRepository;
import com.ifoto.ifoto_backend.repository.ReceiptRepository;
import com.ifoto.ifoto_backend.service.ReceiptService;
import com.ifoto.ifoto_backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock private ReceiptRepository receiptRepository;
    @Mock private EquipmentRentalRepository rentalRepository;
    @Mock private UserService userService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private ReceiptService service;

    private User renter;
    private EquipmentRental rental;

    @BeforeEach
    void setUp() {
        renter = User.builder().id(1L).username("alice").email("alice@test.com").roles(new HashSet<>()).build();
        rental = EquipmentRental.builder()
                .id(10L).rentalNumber("ER-2026-000001").renter(renter).build();
    }

    // ── createOverdueInvoice ─────────────────────────────────────────────────

    @Test
    void createOverdueInvoice_alreadyExists_returnsNull() {
        Receipt existing = Receipt.builder().id(1L).receiptNumber("OI1110001").build();
        when(receiptRepository.findByEquipmentRentalIdAndDocumentType(10L, DocumentType.OVERDUE_INVOICE))
                .thenReturn(Optional.of(existing));

        Receipt result = service.createOverdueInvoice(rental);

        assertNull(result);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void createOverdueInvoice_doesNotExist_createsAndPublishesEvent() {
        when(receiptRepository.findByEquipmentRentalIdAndDocumentType(10L, DocumentType.OVERDUE_INVOICE))
                .thenReturn(Optional.empty());

        Receipt savedOnce = Receipt.builder().id(5L).receiptNumber("TEMP").build();
        Receipt savedTwice = Receipt.builder().id(5L).receiptNumber("OI1110005").build();
        when(receiptRepository.save(any()))
                .thenReturn(savedOnce)
                .thenReturn(savedTwice);
        when(receiptRepository.findFirstByEquipmentRentalIdAndReceiptNumberNotOrderByIdAsc(10L, "TEMP"))
                .thenReturn(Optional.empty());

        Receipt result = service.createOverdueInvoice(rental);

        assertNotNull(result);
        verify(receiptRepository, times(2)).save(any());
        verify(eventPublisher).publishEvent(any());
    }

    // ── checkRentalAccess ─────────────────────────────────────────────────────

    @Test
    void checkRentalAccess_userIsOwner_doesNotThrow() {
        when(rentalRepository.findById(10L)).thenReturn(Optional.of(rental));
        when(userService.findByUsername("alice")).thenReturn(Optional.of(renter));

        assertDoesNotThrow(() -> service.checkRentalAccess(10L, "alice"));
    }

    @Test
    void checkRentalAccess_userIsEquipmentCommittee_doesNotThrow() {
        User committee = User.builder().id(99L).username("comm")
                .roles(Set.of(Role.builder().name("ROLE_EQUIPMENT_COMMITTEE").build())).build();
        when(rentalRepository.findById(10L)).thenReturn(Optional.of(rental));
        when(userService.findByUsername("comm")).thenReturn(Optional.of(committee));

        assertDoesNotThrow(() -> service.checkRentalAccess(10L, "comm"));
    }

    @Test
    void checkRentalAccess_neitherOwnerNorCommittee_throwsForbidden() {
        User other = User.builder().id(99L).username("other").roles(new HashSet<>()).build();
        when(rentalRepository.findById(10L)).thenReturn(Optional.of(rental));
        when(userService.findByUsername("other")).thenReturn(Optional.of(other));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.checkRentalAccess(10L, "other"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
