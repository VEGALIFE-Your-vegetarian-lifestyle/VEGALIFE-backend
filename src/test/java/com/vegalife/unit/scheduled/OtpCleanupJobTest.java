package com.vegalife.unit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.repository.token.OtpCodeRepository;
import com.vegalife.scheduled.OtpCleanupJob;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OtpCleanupJobTest {

  @Mock private OtpCodeRepository otpCodeRepository;

  private OtpCleanupJob otpCleanupJob;

  @BeforeEach
  void setUp() {
    otpCleanupJob = new OtpCleanupJob(otpCodeRepository);
  }

  @Test
  void shouldDeleteExpiredOtpCodesWithCurrentTime() {
    when(otpCodeRepository.deleteExpired(any(Instant.class))).thenReturn(3);

    otpCleanupJob.cleanupExpiredOtpCodes();

    ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
    verify(otpCodeRepository).deleteExpired(captor.capture());
    assertThat(captor.getValue()).isNotNull();
    assertThat(captor.getValue()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void shouldCompleteWhenNoExpiredRowsExist() {
    when(otpCodeRepository.deleteExpired(any(Instant.class))).thenReturn(0);

    otpCleanupJob.cleanupExpiredOtpCodes();

    verify(otpCodeRepository).deleteExpired(any(Instant.class));
  }
}
