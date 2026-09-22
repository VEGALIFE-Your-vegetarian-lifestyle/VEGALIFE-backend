package com.vegalife.repository.token;

import com.vegalife.model.token.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Query("SELECT rt FROM RefreshToken rt WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
  Optional<RefreshToken> findActiveByUserId(@Param("userId") UUID userId);

  @Modifying
  @Transactional
  @Query(
      "UPDATE RefreshToken rt SET rt.revokedAt = :now WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
  int revokeByUserId(@Param("userId") UUID userId, @Param("now") java.time.Instant now);

  @Modifying
  @Transactional
  @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
  int deleteExpired(@Param("now") java.time.Instant now);
}
