package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.user.id = :userId")
    void deleteAllByUserId(Long userId);

    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.used = true, p.usedAt = :usedAt WHERE p.user.id = :userId AND p.used = false")
    void markAllUnusedAsUsedByUserId(@Param("userId") Long userId, @Param("usedAt") Instant usedAt);

    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < :now OR p.used = true")
    void deleteAllExpiredOrUsed(Instant now);
}
