package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.EventDTO.EventRequest;
import com.ifoto.ifoto_backend.dto.EventDTO.EventResponse;
import com.ifoto.ifoto_backend.model.Event;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final String ROLE_EVENT_COMMITTEE = "ROLE_EVENT_COMMITTEE";

    private final EventRepository eventRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EventResponse createEvent(EventRequest request) {
        Event event = Event.builder()
                .eventName(request.eventName())
                .description(request.description())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .location(request.location())
                .isActive(request.isActive() != null ? request.isActive() : true)
                .eventCommittee(new ArrayList<>())
                .build();

        Event saved = eventRepository.save(event);

        if (request.committeeUserIds() != null && !request.committeeUserIds().isEmpty()) {
            List<User> members = resolveUsers(request.committeeUserIds());
            saved.setEventCommittee(members);
            members.forEach(u -> userService.addRoleToUser(u.getId(), ROLE_EVENT_COMMITTEE));
            eventRepository.save(saved);
        }

        return toResponse(saved);
    }

    @Transactional
    public EventResponse updateEvent(Long id, EventRequest request) {
        Event event = findById(id);

        event.setEventName(request.eventName());
        event.setDescription(request.description());
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setLocation(request.location());
        if (request.isActive() != null) {
            event.setActive(request.isActive());
        }

        if (request.committeeUserIds() != null) {
            Set<Long> oldIds = event.getEventCommittee().stream()
                    .map(User::getId)
                    .collect(Collectors.toSet());
            Set<Long> newIds = Set.copyOf(request.committeeUserIds());

            List<Long> removedIds = oldIds.stream().filter(uid -> !newIds.contains(uid)).toList();
            List<Long> addedIds = newIds.stream().filter(uid -> !oldIds.contains(uid)).toList();

            event.setEventCommittee(resolveUsers(request.committeeUserIds()));
            eventRepository.save(event);

            addedIds.forEach(uid -> userService.addRoleToUser(uid, ROLE_EVENT_COMMITTEE));

            removedIds.forEach(uid -> {
                if (!eventRepository.isUserCommitteeInOtherEvent(uid, id)) {
                    userService.removeRoleFromUser(uid, ROLE_EVENT_COMMITTEE);
                }
            });
        }

        return toResponse(eventRepository.save(event));
    }

    @Transactional
    public EventResponse deleteEvent(Long id) {
        Event event = findById(id);
        List<Long> committeeIds = event.getEventCommittee().stream()
                .map(User::getId)
                .toList();
        EventResponse response = toResponse(event);

        eventRepository.delete(event);
        eventRepository.flush();

        committeeIds.forEach(uid -> {
            if (!eventRepository.isUserInAnyCommittee(uid)) {
                userService.removeRoleFromUser(uid, ROLE_EVENT_COMMITTEE);
            }
        });

        return response;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<User> resolveUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return new ArrayList<>();
        return userIds.stream()
                .map(userService::getUserById)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Event findById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + id));
    }

    private EventResponse toResponse(Event event) {
        List<EventResponse.CommitteeMemberResponse> committee = event.getEventCommittee().stream()
                .map(u -> new EventResponse.CommitteeMemberResponse(u.getId(), u.getUsername(), u.getFullName()))
                .toList();

        return new EventResponse(
                event.getEventId(),
                event.getEventName(),
                event.getDescription(),
                event.getStartDate(),
                event.getEndDate(),
                event.getLocation(),
                event.isActive(),
                committee);
    }
}
