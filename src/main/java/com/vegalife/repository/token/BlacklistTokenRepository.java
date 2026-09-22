package com.vegalife.repository.token;

import com.vegalife.model.token.BlacklistToken;
import com.vegalife.model.token.BlacklistTokenId;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlacklistTokenRepository extends JpaRepository<BlacklistToken, BlacklistTokenId> {

  @Query(
      "SELECT CASE WHEN COUNT(bt) > 0 THEN true ELSE false END FROM BlacklistToken bt WHERE bt.jti = :jti AND bt.issuer = :issuer")
  boolean existsByJtiAndIssuer(@Param("jti") String jti, @Param("issuer") String issuer);

  @Query("DELETE FROM BlacklistToken bt WHERE bt.expiresAt < :now")
  int deleteExpired(@Param("now") Instant now);
}
