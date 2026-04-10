package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT e FROM Event e JOIN e.eventCommittee u WHERE u.id = :userId")
    List<Event> findByCommitteeMemberId(@Param("userId") Long userId);

    @Query("SELECT COUNT(e) > 0 FROM Event e JOIN e.eventCommittee u WHERE u.id = :userId AND e.eventId <> :excludeEventId")
    boolean isUserCommitteeInOtherEvent(@Param("userId") Long userId, @Param("excludeEventId") Long excludeEventId);

    @Query("SELECT COUNT(e) > 0 FROM Event e JOIN e.eventCommittee u WHERE u.id = :userId")
    boolean isUserInAnyCommittee(@Param("userId") Long userId);
}
