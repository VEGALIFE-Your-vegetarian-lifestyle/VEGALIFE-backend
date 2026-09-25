package com.vegalife.scheduled;

import com.vegalife.repository.token.OtpCodeRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OtpCleanupJob {

  private final OtpCodeRepository otpCodeRepository;

  @Scheduled(cron = "0 30 3 * * *") // Runs daily at 3:30 AM, after TokenCleanupJob
  public void cleanupExpiredOtpCodes() {
    log.info("Starting scheduled OTP cleanup job");

    int deleted = otpCodeRepository.deleteExpired(Instant.now());

    log.info("OTP cleanup completed: {} expired OTP codes removed", deleted);
  }
}
