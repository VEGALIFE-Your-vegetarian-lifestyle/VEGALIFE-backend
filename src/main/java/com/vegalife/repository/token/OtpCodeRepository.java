package com.vegalife.repository.token;

import com.vegalife.model.token.OtpCode;
import com.vegalife.model.token.OtpPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

  @Query(
      "SELECT o FROM OtpCode o WHERE o.user.id = :userId AND o.purpose = :purpose"
          + " AND o.usedAt IS NULL ORDER BY o.createdAt DESC")
  Optional<OtpCode> findLatestUnusedByUserIdAndPurpose(
      @Param("userId") UUID userId, @Param("purpose") OtpPurpose purpose);

  @Modifying
  @Transactional
  @Query(
      "UPDATE OtpCode o SET o.usedAt = :now WHERE o.user.id = :userId"
          + " AND o.purpose = :purpose AND o.usedAt IS NULL")
  int markAllUnusedByUserIdAndPurpose(
      @Param("userId") UUID userId,
      @Param("purpose") OtpPurpose purpose,
      @Param("now") Instant now);

  @Modifying
  @Transactional
  @Query("DELETE FROM OtpCode o WHERE o.expiresAt < :now")
  int deleteExpired(@Param("now") Instant now);
}
