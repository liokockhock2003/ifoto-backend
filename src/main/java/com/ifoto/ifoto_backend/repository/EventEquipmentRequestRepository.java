package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.Event;
import com.ifoto.ifoto_backend.model.EventEquipmentRequest;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventEquipmentRequestRepository extends JpaRepository<EventEquipmentRequest, Long> {

    List<EventEquipmentRequest> findByRequestedByOrderByCreatedAtDesc(User requestedBy);

    List<EventEquipmentRequest> findByEventOrderByCreatedAtDesc(Event event);

    @Query("""
            SELECT r FROM EventEquipmentRequest r
            LEFT JOIN r.requestedBy u
            LEFT JOIN r.event e
            WHERE (:status IS NULL OR r.status = :status)
              AND (:search IS NULL OR :search = ''
                   OR LOWER(r.requestNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(e.eventName) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY r.createdAt DESC
            """)
    Page<EventEquipmentRequest> searchRequests(
            @Param("search") String search,
            @Param("status") EventEquipmentRequestStatus status,
            Pageable pageable);

    List<EventEquipmentRequest> findByStatusAndApprovedStartDateLessThanEqual(
            EventEquipmentRequestStatus status, java.time.LocalDate date);
}
