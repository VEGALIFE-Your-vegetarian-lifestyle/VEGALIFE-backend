package com.vegalife.service.admin;

import com.vegalife.dto.request.admin.DashboardRequest;
import com.vegalife.dto.response.admin.DashboardChartsMetaResponse;
import com.vegalife.dto.response.admin.DashboardChartsResponse;
import com.vegalife.dto.response.admin.DashboardContentMetrics;
import com.vegalife.dto.response.admin.DashboardEngagementMetrics;
import com.vegalife.dto.response.admin.DashboardMetaResponse;
import com.vegalife.dto.response.admin.DashboardModerationQueueMetrics;
import com.vegalife.dto.response.admin.DashboardMoneyMetrics;
import com.vegalife.dto.response.admin.DashboardResponse;
import com.vegalife.dto.response.admin.DashboardUserMetrics;
import com.vegalife.dto.response.admin.PublishedPostsResponse;
import com.vegalife.dto.response.admin.RevenueByPlanResponse;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.post.VoteType;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.repository.admin.AdminDashboardChartRepository;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.VoteRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.saved.SavedItemRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.AiUsageRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate platform metrics for the admin console, split by whether they move over time.
 *
 * <p>{@link #getSummary()} is a lifetime / current-state snapshot and takes no parameters; {@link
 * #getCharts(DashboardRequest)} is the only time-filtered surface. Keeping them apart means the
 * cheap summary is not forced to age at the pace of the bucketed chart queries, and the summary
 * needs no window resolution at all.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

  /** A pending payment older than this is treated as money that never arrived. */
  private static final Duration STALE_PAYMENT_AGE = Duration.ofHours(24);

  private static final String BOUNDARY_INCLUSIVE_START_EXCLUSIVE_END = "inclusiveStartExclusiveEnd";

  /**
   * The only currency the platform settles in, so it is a constant rather than something read back
   * from the ledger. Every amount in the dashboard is in whole dong: {@code payment_ledger.amount}
   * already is, even though the gateway notifies hundredths.
   */
  private static final String SETTLEMENT_CURRENCY = "VND";

  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final RecipeRepository recipeRepository;
  private final CommentRepository commentRepository;
  private final VoteRepository voteRepository;
  private final SavedItemRepository savedItemRepository;
  private final PaymentLedgerRepository paymentLedgerRepository;
  private final AiSubscriptionRepository aiSubscriptionRepository;
  private final AiUsageRepository aiUsageRepository;
  private final AdminDashboardChartRepository chartRepository;

  /**
   * Lifetime and current-state metrics. No time filter: every value either never resets or
   * describes what is true right now.
   */
  @Transactional(readOnly = true)
  @Cacheable(cacheNames = AdminDashboardCacheNames.ADMIN_DASHBOARD_SUMMARY_CACHE)
  public DashboardResponse getSummary() {
    Instant now = Instant.now();

    return DashboardResponse.builder()
        .meta(meta(now))
        .users(userMetrics())
        .content(contentMetrics())
        .engagement(engagementMetrics())
        .moderationQueue(moderationQueueMetrics(now))
        .money(moneyMetrics())
        .build();
  }

  /** Bucketed time series for the selected range. The only dashboard surface with a time filter. */
  @Transactional(readOnly = true)
  @Cacheable(
      cacheNames = AdminDashboardCacheNames.ADMIN_DASHBOARD_CHARTS_CACHE,
      key = "#request.range")
  public DashboardChartsResponse getCharts(DashboardRequest request) {
    Instant to = Instant.now();
    String interval = intervalFor(request.getRange());
    Instant from = fromFor(request.getRange(), to, interval);
    List<Instant> buckets = bucketStarts(from, interval, to);

    return DashboardChartsResponse.builder()
        .meta(
            DashboardChartsMetaResponse.builder()
                .range(request.getRange())
                .interval(interval)
                .from(from)
                .to(to)
                .boundary(BOUNDARY_INCLUSIVE_START_EXCLUSIVE_END)
                .currency(SETTLEMENT_CURRENCY)
                .generatedAt(to)
                .build())
        .revenueByPlan(revenueByPlan(from, to))
        .publishedPosts(publishedPosts(from, to, interval, buckets))
        .build();
  }

  private DashboardMetaResponse meta(Instant now) {
    return DashboardMetaResponse.builder().generatedAt(now).currency(SETTLEMENT_CURRENCY).build();
  }

  private DashboardUserMetrics userMetrics() {
    long total = userRepository.countByDeletedAtIsNull();
    long verified = userRepository.countByEmailVerifiedTrueAndDeletedAtIsNull();

    return DashboardUserMetrics.builder()
        .total(total)
        .emailVerified(verified)
        .activatedEver(aiUsageRepository.countByRequestCountGreaterThan(0))
        .suspended(userRepository.countByStatusAndDeletedAtIsNull(UserStatus.suspended))
        .deactivated(userRepository.countByStatusAndDeletedAtIsNull(UserStatus.deactivated))
        .activationRate(rate(verified, total))
        .build();
  }

  private DashboardContentMetrics contentMetrics() {
    return DashboardContentMetrics.builder()
        .totalPosts(postRepository.countByDeletedAtIsNull())
        .totalRecipes(recipeRepository.countByDeletedAtIsNull())
        .totalComments(commentRepository.countByDeletedAtIsNull())
        .build();
  }

  private DashboardEngagementMetrics engagementMetrics() {
    long savedPosts = savedItemRepository.countByPostIdIsNotNull();
    long savedRecipes = savedItemRepository.countByRecipeIdIsNotNull();

    return DashboardEngagementMetrics.builder()
        .totalVotes(voteRepository.count())
        .votesUp(voteRepository.countByVoteType(VoteType.UPVOTE))
        .votesDown(voteRepository.countByVoteType(VoteType.DOWNVOTE))
        .totalSaves(savedPosts + savedRecipes)
        .savedPosts(savedPosts)
        .savedRecipes(savedRecipes)
        .build();
  }

  private DashboardModerationQueueMetrics moderationQueueMetrics(Instant now) {
    return DashboardModerationQueueMetrics.builder()
        .pendingPosts(postRepository.countByStatusAndDeletedAtIsNull(PostStatus.pending))
        .pendingRecipes(
            recipeRepository.countByModerationStatusAndDeletedAtIsNull(ModerationStatus.PENDING))
        .reportedComments(commentRepository.countByReportCountGreaterThanAndDeletedAtIsNull(0))
        .oldestPendingAgeHours(oldestPendingAgeHours(now))
        .filterSkippedPosts(postRepository.countByFlagAndDeletedAtIsNull(PostFlag.FILTER_SKIPPED))
        .build();
  }

  private DashboardMoneyMetrics moneyMetrics() {
    long revenue = paymentLedgerRepository.sumAmountByStatus(PaymentLedgerStatus.succeeded);
    long transactions = paymentLedgerRepository.countByStatus(PaymentLedgerStatus.succeeded);
    long payingUsers =
        paymentLedgerRepository.countDistinctUsersByStatus(PaymentLedgerStatus.succeeded);
    long totalUsers = userRepository.countByDeletedAtIsNull();

    return DashboardMoneyMetrics.builder()
        .revenueTotal(revenue)
        .transactionsTotal(transactions)
        .payingUsersTotal(payingUsers)
        .averageOrderValue(rate(revenue, transactions))
        .arpu(rate(revenue, payingUsers))
        .freeGrantsTotal(aiSubscriptionRepository.countByPaymentIdIsNullAndGrantedAtIsNotNull())
        .payerConversionRate(rate(payingUsers, totalUsers))
        .pendingStaleCount(
            paymentLedgerRepository.countByStatusAndCreatedAtBefore(
                PaymentLedgerStatus.pending, Instant.now().minus(STALE_PAYMENT_AGE)))
        .build();
  }

  /**
   * Age of the single oldest item across the three queues, in whole hours.
   *
   * <p>Deliberately a minimum across all three rather than a per-queue age: the admin needs the one
   * number that says how far behind the platform is, not three numbers to compare.
   */
  private long oldestPendingAgeHours(Instant now) {
    Optional<Instant> oldest =
        List.of(
                postRepository.findOldestCreatedAtByStatus(PostStatus.pending),
                recipeRepository.findOldestCreatedAtByModerationStatus(ModerationStatus.PENDING),
                commentRepository.findOldestReportedCreatedAt())
            .stream()
            .flatMap(Optional::stream)
            .min(Comparator.naturalOrder());

    return oldest
        .map(createdAt -> Math.max(0, Duration.between(createdAt, now).toHours()))
        .orElse(0L);
  }

  private RevenueByPlanResponse revenueByPlan(Instant from, Instant to) {
    List<RevenueByPlanResponse.Item> items =
        chartRepository.findRevenueByPlan(from, to).stream()
            .map(
                row ->
                    new RevenueByPlanResponse.Item(
                        row.planId(),
                        row.code(),
                        row.name(),
                        row.amount(),
                        row.purchases(),
                        row.active()))
            .toList();

    return RevenueByPlanResponse.builder().items(items).build();
  }

  /**
   * Published posts per bucket. Like every other series it is zero-filled from the shared bucket
   * list, so a quiet day reads as zero rather than as a gap in the axis.
   */
  private PublishedPostsResponse publishedPosts(
      Instant from, Instant to, String interval, List<Instant> buckets) {
    Map<Instant, Long> published =
        countByBucket(chartRepository.findPublishedPostsPerBucket(from, to, interval));

    List<PublishedPostsResponse.Point> points = new ArrayList<>(buckets.size());
    for (Instant bucket : buckets) {
      points.add(new PublishedPostsResponse.Point(bucket, published.getOrDefault(bucket, 0L)));
    }

    return PublishedPostsResponse.builder().points(points).build();
  }

  private static Map<Instant, Long> countByBucket(
      List<AdminDashboardChartRepository.BucketCountRow> rows) {
    Map<Instant, Long> byBucket = new LinkedHashMap<>();
    rows.forEach(row -> byBucket.put(row.bucketStart(), row.count()));
    return byBucket;
  }

  /**
   * Bucket size, derived from the range so a caller cannot desynchronise the series: {@code day} is
   * sliced hourly, {@code week} and {@code month} daily.
   */
  private static String intervalFor(String range) {
    return switch (range) {
      case "day" -> "hour";
      default -> "day";
    };
  }

  /**
   * Window start, aligned to the bucket that begins {@code buckets - 1} intervals before the
   * current one, so the series always has exactly {@code buckets} points and the last one is the
   * partial bucket in progress.
   *
   * <p>Aligning the start is what keeps the count stable: subtracting a raw duration from {@code
   * now} would land mid-bucket and produce one extra point. {@code month} is the last 30 days
   * rather than a calendar month, so its point count never varies with month length.
   */
  private static Instant fromFor(String range, Instant to, String interval) {
    Instant toBucket = truncate(to, interval);
    return switch (range) {
      case "day" -> toBucket.minus(23, ChronoUnit.HOURS);
      case "month" -> toBucket.minus(29, ChronoUnit.DAYS);
      default -> toBucket.minus(6, ChronoUnit.DAYS);
    };
  }

  /**
   * Every bucket start in the window, so an empty period is reported as zero rather than as a gap
   * in the series. Bounded by the range presets: at most 31 points.
   */
  private static List<Instant> bucketStarts(Instant from, String interval, Instant to) {
    List<Instant> buckets = new ArrayList<>();
    Instant cursor = truncate(from, interval);
    while (cursor.isBefore(to)) {
      buckets.add(cursor);
      cursor =
          switch (interval) {
            case "hour" -> cursor.plus(1, ChronoUnit.HOURS);
            default -> cursor.plus(1, ChronoUnit.DAYS);
          };
    }
    return buckets;
  }

  private static Instant truncate(Instant instant, String interval) {
    if ("hour".equals(interval)) {
      return instant.truncatedTo(ChronoUnit.HOURS);
    }
    return instant.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  /** {@code numerator / denominator} as a fraction, zero when the denominator is zero. */
  private static double rate(long numerator, long denominator) {
    return denominator == 0 ? 0.0 : (double) numerator / denominator;
  }
}
