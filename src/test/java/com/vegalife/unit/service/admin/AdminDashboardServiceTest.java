package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.admin.DashboardRequest;
import com.vegalife.dto.response.admin.DashboardChartsResponse;
import com.vegalife.dto.response.admin.DashboardResponse;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.post.VoteType;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.repository.admin.AdminDashboardChartRepository;
import com.vegalife.repository.admin.AdminDashboardChartRepository.BucketCountRow;
import com.vegalife.repository.admin.AdminDashboardChartRepository.PendingBucketRow;
import com.vegalife.repository.admin.AdminDashboardChartRepository.PlanRevenueRow;
import com.vegalife.repository.admin.AdminDashboardChartRepository.RevenueRow;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.VoteRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.saved.SavedItemRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.admin.AdminDashboardService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PostRepository postRepository;
  @Mock private RecipeRepository recipeRepository;
  @Mock private CommentRepository commentRepository;
  @Mock private VoteRepository voteRepository;
  @Mock private SavedItemRepository savedItemRepository;
  @Mock private PaymentLedgerRepository paymentLedgerRepository;
  @Mock private AiSubscriptionRepository aiSubscriptionRepository;
  @Mock private AiUsageRepository aiUsageRepository;
  @Mock private AdminDashboardChartRepository chartRepository;

  @InjectMocks private AdminDashboardService adminDashboardService;

  // ---------------------------------------------------------------------
  // Summary: lifetime, no time filter
  // ---------------------------------------------------------------------

  @Test
  void getSummaryAggregatesLifetimeMetrics() {
    when(userRepository.countByDeletedAtIsNull()).thenReturn(100L);
    when(userRepository.countByEmailVerifiedTrueAndDeletedAtIsNull()).thenReturn(80L);
    when(aiUsageRepository.countByRequestCountGreaterThan(0)).thenReturn(60L);
    when(userRepository.countByStatusAndDeletedAtIsNull(UserStatus.suspended)).thenReturn(3L);
    when(userRepository.countByStatusAndDeletedAtIsNull(UserStatus.deactivated)).thenReturn(2L);

    when(postRepository.countByDeletedAtIsNull()).thenReturn(50L);
    when(recipeRepository.countByDeletedAtIsNull()).thenReturn(20L);
    when(commentRepository.countByDeletedAtIsNull()).thenReturn(200L);

    when(voteRepository.count()).thenReturn(120L);
    when(voteRepository.countByVoteType(VoteType.UPVOTE)).thenReturn(100L);
    when(voteRepository.countByVoteType(VoteType.DOWNVOTE)).thenReturn(20L);
    when(savedItemRepository.countByPostIdIsNotNull()).thenReturn(20L);
    when(savedItemRepository.countByRecipeIdIsNotNull()).thenReturn(10L);

    when(postRepository.countByStatusAndDeletedAtIsNull(PostStatus.pending)).thenReturn(4L);
    when(recipeRepository.countByModerationStatusAndDeletedAtIsNull(ModerationStatus.PENDING))
        .thenReturn(2L);
    when(commentRepository.countByReportCountGreaterThanAndDeletedAtIsNull(0)).thenReturn(6L);
    when(postRepository.countByFlagAndDeletedAtIsNull(PostFlag.FILTER_SKIPPED)).thenReturn(1L);

    when(paymentLedgerRepository.sumAmountByStatus(PaymentLedgerStatus.succeeded))
        .thenReturn(500_000L);
    when(paymentLedgerRepository.countByStatus(PaymentLedgerStatus.succeeded)).thenReturn(5L);
    when(paymentLedgerRepository.countDistinctUsersByStatus(PaymentLedgerStatus.succeeded))
        .thenReturn(4L);
    when(aiSubscriptionRepository.countByPaymentIdIsNullAndGrantedAtIsNotNull()).thenReturn(7L);
    when(paymentLedgerRepository.countByStatusAndCreatedAtBefore(
            eq(PaymentLedgerStatus.pending), any()))
        .thenReturn(3L);

    DashboardResponse response = adminDashboardService.getSummary();

    assertThat(response.getUsers().getTotal()).isEqualTo(100L);
    assertThat(response.getUsers().getEmailVerified()).isEqualTo(80L);
    assertThat(response.getUsers().getActivatedEver()).isEqualTo(60L);
    assertThat(response.getUsers().getSuspended()).isEqualTo(3L);
    assertThat(response.getUsers().getDeactivated()).isEqualTo(2L);
    assertThat(response.getUsers().getActivationRate()).isEqualTo(0.8);

    assertThat(response.getContent().getTotalPosts()).isEqualTo(50L);
    assertThat(response.getContent().getTotalRecipes()).isEqualTo(20L);
    assertThat(response.getContent().getTotalComments()).isEqualTo(200L);

    assertThat(response.getEngagement().getTotalVotes()).isEqualTo(120L);
    assertThat(response.getEngagement().getVotesUp()).isEqualTo(100L);
    assertThat(response.getEngagement().getVotesDown()).isEqualTo(20L);
    assertThat(response.getEngagement().getTotalSaves()).isEqualTo(30L);
    assertThat(response.getEngagement().getSavedPosts()).isEqualTo(20L);
    assertThat(response.getEngagement().getSavedRecipes()).isEqualTo(10L);

    assertThat(response.getModerationQueue().getPendingPosts()).isEqualTo(4L);
    assertThat(response.getModerationQueue().getPendingRecipes()).isEqualTo(2L);
    assertThat(response.getModerationQueue().getReportedComments()).isEqualTo(6L);
    assertThat(response.getModerationQueue().getFilterSkippedPosts()).isEqualTo(1L);

    assertThat(response.getMoney().getRevenueTotal()).isEqualTo(500_000L);
    assertThat(response.getMoney().getTransactionsTotal()).isEqualTo(5L);
    assertThat(response.getMoney().getPayingUsersTotal()).isEqualTo(4L);
    assertThat(response.getMoney().getAverageOrderValue()).isEqualTo(100_000.0);
    assertThat(response.getMoney().getArpu()).isEqualTo(125_000.0);
    assertThat(response.getMoney().getFreeGrantsTotal()).isEqualTo(7L);
    assertThat(response.getMoney().getPayerConversionRate()).isEqualTo(0.04);
    assertThat(response.getMoney().getPendingStaleCount()).isEqualTo(3L);

    assertThat(response.getMeta().getGeneratedAt()).isNotNull();
    assertThat(response.getMeta().getCurrency()).isEqualTo("VND");
  }

  @Test
  void getSummaryReportsZeroRatesRatherThanDividingByZero() {
    DashboardResponse response = adminDashboardService.getSummary();

    assertThat(response.getUsers().getActivationRate()).isZero();
    assertThat(response.getMoney().getAverageOrderValue()).isZero();
    assertThat(response.getMoney().getArpu()).isZero();
    assertThat(response.getMoney().getPayerConversionRate()).isZero();
  }

  @Test
  void getSummaryReportsTheOldestWaitingItemAcrossAllQueues() {
    Instant now = Instant.now();
    when(postRepository.findOldestCreatedAtByStatus(PostStatus.pending))
        .thenReturn(Optional.of(now.minus(Duration.ofHours(10))));
    when(recipeRepository.findOldestCreatedAtByModerationStatus(ModerationStatus.PENDING))
        .thenReturn(Optional.of(now.minus(Duration.ofHours(30))));
    when(commentRepository.findOldestReportedCreatedAt())
        .thenReturn(Optional.of(now.minus(Duration.ofHours(2))));

    DashboardResponse response = adminDashboardService.getSummary();

    assertThat(response.getModerationQueue().getOldestPendingAgeHours()).isBetween(29L, 30L);
  }

  @Test
  void getSummaryReportsZeroAgeWhenNoQueueHasAnythingWaiting() {
    DashboardResponse response = adminDashboardService.getSummary();

    assertThat(response.getModerationQueue().getOldestPendingAgeHours()).isZero();
  }

  // ---------------------------------------------------------------------
  // Charts: the only time-filtered surface
  // ---------------------------------------------------------------------

  @Test
  void getChartsForWeekProducesSevenDailyBuckets() {
    DashboardChartsResponse response =
        adminDashboardService.getCharts(DashboardRequest.builder().range("week").build());

    assertThat(response.getMeta().getRange()).isEqualTo("week");
    assertThat(response.getMeta().getInterval()).isEqualTo("day");
    assertThat(response.getMeta().getBoundary()).isEqualTo("inclusiveStartExclusiveEnd");
    assertThat(response.getMeta().getCurrency()).isEqualTo("VND");
    assertThat(response.getRevenueTrend().getPoints()).hasSize(7);
    assertThat(response.getPaymentHealth().getPoints()).hasSize(7);
    assertThat(response.getGrowth().getPoints()).hasSize(7);
  }

  @Test
  void getChartsForDayProducesTwentyFourHourlyBuckets() {
    DashboardChartsResponse response =
        adminDashboardService.getCharts(DashboardRequest.builder().range("day").build());

    assertThat(response.getMeta().getInterval()).isEqualTo("hour");
    assertThat(response.getRevenueTrend().getPoints()).hasSize(24);
  }

  @Test
  void getChartsForMonthProducesThirtyDailyBuckets() {
    DashboardChartsResponse response =
        adminDashboardService.getCharts(DashboardRequest.builder().range("month").build());

    assertThat(response.getMeta().getInterval()).isEqualTo("day");
    assertThat(response.getRevenueTrend().getPoints()).hasSize(30);
  }

  @Test
  void getChartsZeroFillsBucketsWithNoRows() {
    when(chartRepository.findRevenueTrend(any(), any(), anyString()))
        .thenReturn(List.of(new RevenueRow(Instant.parse("2020-01-01T00:00:00Z"), 900L, 3L)));

    DashboardChartsResponse response =
        adminDashboardService.getCharts(DashboardRequest.builder().range("week").build());

    long total = response.getRevenueTrend().getPoints().stream().mapToLong(p -> p.amount()).sum();
    // The stubbed row falls outside the real window, so every bucket must be reported as zero.
    assertThat(response.getRevenueTrend().getPoints()).hasSize(7);
    assertThat(total).isZero();
  }

  @Test
  void getChartsDerivesSuccessRateFromDecidedOutcomesOnly() {
    when(chartRepository.findSucceededPerBucket(any(), any(), anyString()))
        .thenReturn(List.of(new BucketCountRow(Instant.now(), 8L)));
    when(chartRepository.findFailedPerBucket(any(), any(), anyString()))
        .thenReturn(List.of(new BucketCountRow(Instant.now(), 2L)));

    DashboardChartsResponse response =
        adminDashboardService.getCharts(DashboardRequest.builder().range("week").build());

    assertThat(response.getPaymentHealth().getTotals().successRate()).isEqualTo(0.8);
  }

  @Test
  void getChartsSeparatesFreshAndStalePendingPayments() {
    when(chartRepository.findPendingPerBucket(any(), any(), any(), anyString()))
        .thenReturn(List.of(new PendingBucketRow(Instant.now(), 3L, 5L)));

    DashboardChartsResponse response =
        adminDashboardService.getCharts(DashboardRequest.builder().range("week").build());

    assertThat(response.getPaymentHealth().getTotals().pendingFresh()).isEqualTo(3L);
    assertThat(response.getPaymentHealth().getTotals().pendingStale()).isEqualTo(5L);
  }

  @Test
  void getChartsRanksPlansAndKeepsDeactivatedOnesThatEarned() {
    UUID planId = UUID.randomUUID();
    when(chartRepository.findRevenueByPlan(any(), any()))
        .thenReturn(List.of(new PlanRevenueRow(planId, "PRO", "Pro", 490_000L, 10L, false)));

    DashboardChartsResponse response =
        adminDashboardService.getCharts(DashboardRequest.builder().range("week").build());

    assertThat(response.getRevenueByPlan().getItems()).hasSize(1);
    assertThat(response.getRevenueByPlan().getItems().get(0).code()).isEqualTo("PRO");
    assertThat(response.getRevenueByPlan().getItems().get(0).active()).isFalse();
  }

  @Test
  void getChartsReportsSignupsAndFirstTimePayersAsSeparateCounts() {
    // The service truncates the window start to build its first bucket, so answering with that same
    // bucket guarantees the rows land in a point instead of being zero-filled away.
    when(chartRepository.findNewUsersPerBucket(any(), any(), anyString()))
        .thenAnswer(
            invocation -> List.of(new BucketCountRow(firstBucket(invocation.getArgument(0)), 40L)));
    when(chartRepository.findNewPayingUsersPerBucket(any(), any(), anyString()))
        .thenAnswer(
            invocation -> List.of(new BucketCountRow(firstBucket(invocation.getArgument(0)), 2L)));

    DashboardChartsResponse response =
        adminDashboardService.getCharts(DashboardRequest.builder().range("week").build());

    long signups = response.getGrowth().getPoints().stream().mapToLong(p -> p.newUsers()).sum();
    long payers =
        response.getGrowth().getPoints().stream().mapToLong(p -> p.newPayingUsers()).sum();
    assertThat(signups).isEqualTo(40L);
    assertThat(payers).isEqualTo(2L);
  }

  /** The same truncation the service applies, so a stubbed row lands on a real bucket. */
  private static Instant firstBucket(Instant from) {
    return from.atZone(java.time.ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(java.time.ZoneOffset.UTC)
        .toInstant();
  }
}
