package com.vegalife.repository.token;

import com.vegalife.model.token.PasswordResetOtp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, UUID> {

  @Query(
      "SELECT o FROM PasswordResetOtp o WHERE o.user.id = :userId AND o.usedAt IS NULL"
          + " ORDER BY o.createdAt DESC")
  Optional<PasswordResetOtp> findLatestUnusedByUserId(@Param("userId") UUID userId);

  @Modifying
  @Transactional
  @Query(
      "UPDATE PasswordResetOtp o SET o.usedAt = :now WHERE o.user.id = :userId"
          + " AND o.usedAt IS NULL")
  int markAllUnusedByUserId(@Param("userId") UUID userId, @Param("now") java.time.Instant now);

  @Modifying
  @Transactional
  @Query("DELETE FROM PasswordResetOtp o WHERE o.expiresAt < :now")
  int deleteExpired(@Param("now") java.time.Instant now);
}
