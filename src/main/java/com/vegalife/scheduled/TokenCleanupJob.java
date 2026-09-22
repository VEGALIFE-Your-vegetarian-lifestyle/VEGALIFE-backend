package com.vegalife.scheduled;

import com.vegalife.service.token.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupJob {

  private final JwtTokenService jwtTokenService;

  @Scheduled(cron = "0 0 3 * * *") // Runs daily at 3:00 AM
  public void cleanupExpiredTokens() {
    log.info("Starting scheduled token cleanup job");

    int expiredRefreshTokens = jwtTokenService.cleanupExpiredRefreshTokens();
    int expiredBlacklistTokens = jwtTokenService.cleanupExpiredBlacklistTokens();

    log.info(
        "Token cleanup completed: {} expired refresh tokens removed, {} expired blacklist tokens removed",
        expiredRefreshTokens,
        expiredBlacklistTokens);
  }
}
