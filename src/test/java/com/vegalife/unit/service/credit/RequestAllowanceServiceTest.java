package com.vegalife.unit.service.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.service.credit.RequestAllowanceService;
import com.vegalife.shared.exception.AiQuotaExceededException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestAllowanceServiceTest {

  @Mock private AiSubscriptionRepository subscriptionRepository;
  @Mock private AiUsageRepository usageRepository;

  @InjectMocks private RequestAllowanceService requestAllowanceService;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @Test
  void remainingUsage_isAllowanceMinusLifetimeConsumption() {
    when(subscriptionRepository.sumGrantedQuota(userId)).thenReturn(505L);
    when(usageRepository.sumRequestCount(userId)).thenReturn(12L);

    assertThat(requestAllowanceService.getRemainingUsage(userId)).isEqualTo(493L);
  }

  @Test
  void remainingUsage_floorsAtZeroWhenConsumptionExceedsAllowance() {
    when(subscriptionRepository.sumGrantedQuota(userId)).thenReturn(5L);
    when(usageRepository.sumRequestCount(userId)).thenReturn(9L);

    assertThat(requestAllowanceService.getRemainingUsage(userId)).isZero();
  }

  @Test
  void remainingUsage_isZeroWithoutAnyAcquisition() {
    when(subscriptionRepository.sumGrantedQuota(userId)).thenReturn(0L);
    when(usageRepository.sumRequestCount(userId)).thenReturn(0L);

    assertThat(requestAllowanceService.getRemainingUsage(userId)).isZero();
  }

  @Test
  void requireAllowance_returnsRemainingWhenOneIsLeft() {
    when(subscriptionRepository.sumGrantedQuota(userId)).thenReturn(5L);
    when(usageRepository.sumRequestCount(userId)).thenReturn(4L);

    assertThat(requestAllowanceService.requireAllowance(userId)).isEqualTo(1L);
  }

  @Test
  void requireAllowance_throwsWithoutRetryHintWhenExhausted() {
    when(subscriptionRepository.sumGrantedQuota(userId)).thenReturn(5L);
    when(usageRepository.sumRequestCount(userId)).thenReturn(5L);

    assertThatThrownBy(() -> requestAllowanceService.requireAllowance(userId))
        .isInstanceOf(AiQuotaExceededException.class)
        .hasMessage("No AI requests remaining. Claim a free plan or purchase one to continue.");
  }

  @Test
  void record_incrementsTheLifetimeCounter() {
    requestAllowanceService.record(userId);

    verify(usageRepository).incrementRequestCount(userId);
  }
}
