package com.vegalife.repository.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Bucketed aggregates behind the admin dashboard charts.
 *
 * <p>Deliberately {@link JdbcTemplate} rather than JPQL: bucketing needs Postgres {@code
 * date_trunc}, which has no portable JPQL equivalent, and the queries return scalars rather than
 * entities. The trade-off is that these are Postgres-only and therefore exercised by the
 * Testcontainers integration suite rather than the H2 {@code @DataJpaTest} slice — a native query
 * is not parsed at bootstrap, so the H2 context still starts.
 *
 * <p>Every window is half-open, {@code [from, to)}, so adjacent windows cannot both claim a row
 * written exactly on the boundary.
 *
 * <p>{@code date_trunc} takes an explicit {@code 'UTC'} zone. Without it, truncation happens in the
 * session timezone, so a server running in, say, Asia/Bangkok would produce bucket starts at local
 * midnight — which never match the UTC-aligned buckets the service generates, and every series
 * would silently come back empty.
 */
@Repository
@RequiredArgsConstructor
public class AdminDashboardChartRepository {

  private final JdbcTemplate jdbcTemplate;

  /**
   * Succeeded revenue per plan, ranked by amount.
   *
   * <p>The plan is reached through the acquisition row, which owns the plan link since V48; the
   * ledger describes money only. A deactivated plan still appears when it earned inside the window,
   * so the plan breakdown always sums to the same total as the ledger.
   */
  public List<PlanRevenueRow> findRevenueByPlan(Instant from, Instant to) {
    return jdbcTemplate.query(
        "SELECT p.id AS plan_id, p.code AS code, p.name AS name, p.active AS active,"
            + "       sum(l.amount)::bigint AS amount, count(*) AS purchases"
            + "  FROM payment_ledger l"
            + "  JOIN ai_subscription s ON s.payment_id = l.id"
            + "  JOIN ai_plan p ON p.id = s.plan_id"
            + " WHERE l.status = 'succeeded' AND l.paid_at >= ? AND l.paid_at < ?"
            + " GROUP BY p.id, p.code, p.name, p.active"
            + " ORDER BY amount DESC",
        (rs, rowNum) ->
            new PlanRevenueRow(
                rs.getObject("plan_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getLong("amount"),
                rs.getLong("purchases"),
                rs.getBoolean("active")),
        timestamp(from),
        timestamp(to));
  }

  /**
   * Posts that reached readers per bucket.
   *
   * <p>Only {@code published} and not soft-deleted: drafts and anything still in the moderation
   * queue are not content readers ever saw. Bucketed on {@code published_at} rather than {@code
   * created_at} so a post written earlier and released today lands in today's bucket. Published
   * posts always carry a {@code published_at}, so no row is silently dropped by the range filter.
   */
  public List<BucketCountRow> findPublishedPostsPerBucket(
      Instant from, Instant to, String interval) {
    return jdbcTemplate.query(
        "SELECT date_trunc(cast(? AS text), p.published_at, 'UTC') AS bucket_start,"
            + "       count(*) AS cnt"
            + "  FROM post p"
            + " WHERE p.status = 'published' AND p.deleted_at IS NULL"
            + "   AND p.published_at >= ? AND p.published_at < ?"
            + " GROUP BY bucket_start",
        (rs, rowNum) ->
            new BucketCountRow(
                bucketStart(rs.getObject("bucket_start", OffsetDateTime.class)), rs.getLong("cnt")),
        interval,
        timestamp(from),
        timestamp(to));
  }

  private static Instant bucketStart(OffsetDateTime bucketStart) {
    return bucketStart == null ? null : bucketStart.toInstant();
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.from(instant);
  }

  /** One plan's succeeded revenue inside the window, and how many packages were bought. */
  public record PlanRevenueRow(
      UUID planId, String code, String name, long amount, long purchases, boolean active) {}

  /** A plain count in one bucket. */
  public record BucketCountRow(Instant bucketStart, long count) {}
}
