package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.dto.EventDTO.EventRequest;
import com.ifoto.ifoto_backend.model.Event;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.EventRepository;
import com.ifoto.ifoto_backend.service.EventService;
import com.ifoto.ifoto_backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private UserService userService;

    @InjectMocks private EventService service;

    private User user1;
    private User user2;
    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUp() {
        user1 = User.builder().id(1L).username("u1").build();
        user2 = User.builder().id(2L).username("u2").build();
        start = LocalDateTime.now().plusDays(1);
        end = LocalDateTime.now().plusDays(3);

        lenient().when(eventRepository.save(any())).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            if (e.getEventId() == null) {
                e = Event.builder()
                        .eventId(99L).eventName(e.getEventName())
                        .description(e.getDescription())
                        .startDatetime(e.getStartDatetime()).endDatetime(e.getEndDatetime())
                        .location(e.getLocation()).isActive(e.isActive())
                        .eventCommittee(e.getEventCommittee() != null ? e.getEventCommittee() : new ArrayList<>())
                        .build();
            }
            return e;
        });
    }

    private EventRequest request(List<Long> committeeIds, Boolean isActive) {
        return new EventRequest("Test Event", "desc", start, end, "Room 1", isActive, committeeIds);
    }

    // ── createEvent ──────────────────────────────────────────────────────────

    @Test
    void createEvent_withCommitteeIds_addsAndAssignsRole() {
        when(userService.getUserById(1L)).thenReturn(user1);
        when(userService.getUserById(2L)).thenReturn(user2);

        service.createEvent(request(List.of(1L, 2L), true));

        verify(userService).addRoleToUser(1L, "ROLE_EVENT_COMMITTEE");
        verify(userService).addRoleToUser(2L, "ROLE_EVENT_COMMITTEE");
    }

    @Test
    void createEvent_nullCommitteeIds_noMembersAdded() {
        service.createEvent(request(null, true));

        verify(userService, never()).addRoleToUser(any(), any());
    }

    @Test
    void createEvent_emptyCommitteeIds_noMembersAdded() {
        service.createEvent(request(List.of(), true));

        verify(userService, never()).addRoleToUser(any(), any());
    }

    @Test
    void createEvent_isActiveNull_defaultsToTrue() {
        service.createEvent(request(null, null));

        verify(eventRepository).save(argThat(e -> e.isActive()));
    }

    @Test
    void createEvent_isActiveFalse_setsFalse() {
        service.createEvent(request(null, false));

        verify(eventRepository).save(argThat(e -> !e.isActive()));
    }

    // ── updateEvent ──────────────────────────────────────────────────────────

    private Event existingEvent(List<User> committee) {
        return Event.builder()
                .eventId(99L).eventName("Old").description("d")
                .startDatetime(start).endDatetime(end).location("Room 1")
                .isActive(true).eventCommittee(new ArrayList<>(committee)).build();
    }

    @Test
    void updateEvent_isActiveNotNull_updatesFlag() {
        Event event = existingEvent(List.of());
        when(eventRepository.findById(99L)).thenReturn(Optional.of(event));

        service.updateEvent(99L, request(null, false));

        assertFalse(event.isActive());
    }

    @Test
    void updateEvent_isActiveNull_flagUnchanged() {
        Event event = existingEvent(List.of());
        when(eventRepository.findById(99L)).thenReturn(Optional.of(event));

        service.updateEvent(99L, request(null, null));

        assertTrue(event.isActive());
    }

    @Test
    void updateEvent_committeeIdsNull_skipsMemberReconciliation() {
        Event event = existingEvent(List.of(user1));
        when(eventRepository.findById(99L)).thenReturn(Optional.of(event));

        service.updateEvent(99L, request(null, true));

        verify(userService, never()).addRoleToUser(any(), any());
        verify(userService, never()).removeRoleFromUser(any(), any());
    }

    @Test
    void updateEvent_newMemberAdded_assignsRole() {
        Event event = existingEvent(List.of(user1));
        when(eventRepository.findById(99L)).thenReturn(Optional.of(event));
        when(userService.getUserById(1L)).thenReturn(user1);
        when(userService.getUserById(2L)).thenReturn(user2);

        service.updateEvent(99L, request(List.of(1L, 2L), true));

        verify(userService).addRoleToUser(2L, "ROLE_EVENT_COMMITTEE");
        verify(userService, never()).addRoleToUser(eq(1L), any());
    }

    @Test
    void updateEvent_memberRemoved_notInOtherEvent_removesRole() {
        Event event = existingEvent(List.of(user1, user2));
        when(eventRepository.findById(99L)).thenReturn(Optional.of(event));
        when(userService.getUserById(1L)).thenReturn(user1);
        when(eventRepository.isUserCommitteeInOtherEvent(2L, 99L)).thenReturn(false);

        service.updateEvent(99L, request(List.of(1L), true));

        verify(userService).removeRoleFromUser(2L, "ROLE_EVENT_COMMITTEE");
    }

    @Test
    void updateEvent_memberRemoved_stillInOtherEvent_roleKept() {
        Event event = existingEvent(List.of(user1, user2));
        when(eventRepository.findById(99L)).thenReturn(Optional.of(event));
        when(userService.getUserById(1L)).thenReturn(user1);
        when(eventRepository.isUserCommitteeInOtherEvent(2L, 99L)).thenReturn(true);

        service.updateEvent(99L, request(List.of(1L), true));

        verify(userService, never()).removeRoleFromUser(eq(2L), any());
    }

    // ── deleteEvent ──────────────────────────────────────────────────────────

    @Test
    void deleteEvent_memberNotInOtherEvent_roleRemoved() {
        Event event = existingEvent(List.of(user1));
        when(eventRepository.findById(99L)).thenReturn(Optional.of(event));
        when(eventRepository.isUserInAnyCommittee(1L)).thenReturn(false);

        service.deleteEvent(99L);

        verify(userService).removeRoleFromUser(1L, "ROLE_EVENT_COMMITTEE");
    }

    @Test
    void deleteEvent_memberStillInOtherEvent_roleKept() {
        Event event = existingEvent(List.of(user1));
        when(eventRepository.findById(99L)).thenReturn(Optional.of(event));
        when(eventRepository.isUserInAnyCommittee(1L)).thenReturn(true);

        service.deleteEvent(99L);

        verify(userService, never()).removeRoleFromUser(eq(1L), any());
    }

    @Test
    void deleteEvent_noCommitteeMembers_noRoleRemoval() {
        Event event = existingEvent(List.of());
        when(eventRepository.findById(99L)).thenReturn(Optional.of(event));

        service.deleteEvent(99L);

        verify(userService, never()).removeRoleFromUser(any(), any());
    }
}
