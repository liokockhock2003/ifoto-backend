package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Useful for soft-deleted users
    Optional<User> findByEmailAndIsActiveTrue(String email);

    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN u.roles r
            WHERE (:role IS NULL OR :role = '' OR r.name = :role)
              AND (:search IS NULL OR :search = ''
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<User> searchUsers(@Param("search") String search, @Param("role") String role, Pageable pageable);
}