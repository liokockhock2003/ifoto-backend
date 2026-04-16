package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.used = true, t.usedAt = :usedAt WHERE t.user.id = :userId AND t.used = false")
    void markAllUnusedAsUsedByUserId(@Param("userId") Long userId, @Param("usedAt") Instant usedAt);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :now OR t.used = true")
    void deleteAllExpiredOrUsed(@Param("now") Instant now);
}
