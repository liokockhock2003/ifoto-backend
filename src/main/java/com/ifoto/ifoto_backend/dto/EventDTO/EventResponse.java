package com.ifoto.ifoto_backend.dto.EventDTO;

import java.time.LocalDate;
import java.util.List;

public record EventResponse(
        Long eventId,
        String eventName,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        String location,
        boolean isActive,
        List<CommitteeMemberResponse> eventCommittee) {

    public record CommitteeMemberResponse(Long id, String username, String fullName) {}
}
