package com.vegalife.unit.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.service.ai.AiQuotaGuard;
import com.vegalife.shared.exception.AiQuotaExceededException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiQuotaGuardTest {

  @Mock private AiSubscriptionRepository subscriptionRepository;
  @Mock private AiPlanRepository planRepository;
  @Mock private AiUsageRepository usageRepository;

  @InjectMocks private AiQuotaGuard quotaGuard;

  private UUID userId;
  private AiPlan freePlan;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    freePlan = AiPlan.builder().id(UUID.randomUUID()).code("FREE").monthlyRequestLimit(20).build();
  }

  @Test
  void requireAllowance_withoutSubscription_usesFreeDefaultAndCurrentUtcMonth() {
    YearMonth month = YearMonth.now(ZoneOffset.UTC);
    Instant start = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.empty());
    when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
    when(usageRepository.sumRequestCountInWindow(eq(userId), eq(start), eq(end))).thenReturn(4L);

    AiQuotaGuard.QuotaWindow window = quotaGuard.requireAllowance(userId);

    assertThat(window.start()).isEqualTo(start);
    assertThat(window.end()).isEqualTo(end);
    assertThat(window.limit()).isEqualTo(20);
    assertThat(window.used()).isEqualTo(4L);
  }

  @Test
  void requireAllowance_usesActiveSubscriptionPlanLimit() {
    AiPlan proPlan =
        AiPlan.builder().id(UUID.randomUUID()).code("PRO").monthlyRequestLimit(500).build();
    AiSubscription subscription =
        AiSubscription.builder().userId(userId).planId(proPlan.getId()).build();
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.of(subscription));
    when(planRepository.findById(proPlan.getId())).thenReturn(Optional.of(proPlan));
    when(usageRepository.sumRequestCountInWindow(any(), any(), any())).thenReturn(10L);

    AiQuotaGuard.QuotaWindow window = quotaGuard.requireAllowance(userId);

    assertThat(window.limit()).isEqualTo(500);
    assertThat(window.used()).isEqualTo(10L);
  }

  @Test
  void requireAllowance_atLimit_throwsWithRetryAfterSeconds() {
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.empty());
    when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
    when(usageRepository.sumRequestCountInWindow(any(), any(), any())).thenReturn(20L);

    assertThatThrownBy(() -> quotaGuard.requireAllowance(userId))
        .isInstanceOf(AiQuotaExceededException.class)
        .hasMessageContaining("(20/20)")
        .satisfies(
            ex -> assertThat(((AiQuotaExceededException) ex).getRetryAfterSeconds()).isPositive());
  }

  @Test
  void record_incrementsUsageInTheCheckedWindow() {
    AiQuotaGuard.QuotaWindow window =
        new AiQuotaGuard.QuotaWindow(
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), 20, 3L);

    quotaGuard.record(userId, window);

    verify(usageRepository)
        .incrementRequestCount(
            userId, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"));
  }

  @Test
  void requireAllowance_whenFreePlanMissing_failsFast() {
    when(subscriptionRepository.findInEffect(userId)).thenReturn(Optional.empty());
    when(planRepository.findByCode("FREE")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> quotaGuard.requireAllowance(userId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FREE");

    verify(usageRepository, never()).sumRequestCountInWindow(any(), any(), any());
  }
}
