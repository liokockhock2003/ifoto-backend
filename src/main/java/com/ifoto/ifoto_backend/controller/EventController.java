package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.EventDTO.EventRequest;
import com.ifoto.ifoto_backend.dto.EventDTO.EventResponse;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.service.EventService;
import com.ifoto.ifoto_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<EventResponse>> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @GetMapping("/committee/{userId}")
    public ResponseEntity<List<EventResponse>> getEventsByCommitteeMember(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails principal) {
        User authenticatedUser = userService.getByUsername(principal.getUsername());
        boolean isHighCommittee = authenticatedUser.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_HIGH_COMMITTEE"));
        if (!isHighCommittee && !authenticatedUser.getId().equals(userId)) {
            throw new AccessDeniedException("You can only view your own assigned events");
        }
        return ResponseEntity.ok(eventService.getEventsByCommitteeMember(userId));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<List<EventResponse>> getEventsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(eventService.getEventsByCommitteeMember(userId));
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody EventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<EventResponse> deleteEvent(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.deleteEvent(id));
    }
}
