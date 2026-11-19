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

  /** Succeeded revenue per bucket: the amount received and how many payments produced it. */
  public List<RevenueRow> findRevenueTrend(Instant from, Instant to, String interval) {
    return jdbcTemplate.query(
        "SELECT date_trunc(cast(? AS text), l.paid_at, 'UTC') AS bucket_start,"
            + "       sum(l.amount)::bigint AS amount,"
            + "       count(*) AS tx_count"
            + "  FROM payment_ledger l"
            + " WHERE l.status = 'succeeded' AND l.paid_at >= ? AND l.paid_at < ?"
            + " GROUP BY bucket_start"
            + " ORDER BY bucket_start",
        (rs, rowNum) ->
            new RevenueRow(
                bucketStart(rs.getObject("bucket_start", OffsetDateTime.class)),
                rs.getLong("amount"),
                rs.getLong("tx_count")),
        interval,
        timestamp(from),
        timestamp(to));
  }

  /**
   * Succeeded revenue per plan, ranked by amount.
   *
   * <p>The plan is reached through the acquisition row, which owns the plan link since V48; the
   * ledger describes money only. A deactivated plan still appears when it earned inside the window,
   * so the plan breakdown always sums to the same total as the trend.
   */
  public List<PlanRevenueRow> findRevenueByPlan(Instant from, Instant to) {
    return jdbcTemplate.query(
        "SELECT p.id AS plan_id, p.code AS code, p.name AS name, p.active AS active,"
            + "       sum(l.amount)::bigint AS amount, count(*) AS tx_count"
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
                rs.getLong("tx_count"),
                rs.getBoolean("active")),
        timestamp(from),
        timestamp(to));
  }

  /** Succeeded payments per bucket, bucketed by {@code paid_at} — the moment the money landed. */
  public List<BucketCountRow> findSucceededPerBucket(Instant from, Instant to, String interval) {
    return jdbcTemplate.query(
        "SELECT date_trunc(cast(? AS text), l.paid_at, 'UTC') AS bucket_start, count(*) AS cnt"
            + "  FROM payment_ledger l"
            + " WHERE l.status = 'succeeded' AND l.paid_at >= ? AND l.paid_at < ?"
            + " GROUP BY bucket_start",
        (rs, rowNum) ->
            new BucketCountRow(
                bucketStart(rs.getObject("bucket_start", OffsetDateTime.class)), rs.getLong("cnt")),
        interval,
        timestamp(from),
        timestamp(to));
  }

  /**
   * Failed payments per bucket.
   *
   * <p>Bucketed by {@code updated_at} because the ledger has no {@code failed_at} column: the
   * transition only bumps the Hibernate update timestamp. That is a proxy, not an exact decision
   * time, and the API documents it as such.
   */
  public List<BucketCountRow> findFailedPerBucket(Instant from, Instant to, String interval) {
    return jdbcTemplate.query(
        "SELECT date_trunc(cast(? AS text), l.updated_at, 'UTC') AS bucket_start, count(*) AS cnt"
            + "  FROM payment_ledger l"
            + " WHERE l.status = 'failed' AND l.updated_at >= ? AND l.updated_at < ?"
            + " GROUP BY bucket_start",
        (rs, rowNum) ->
            new BucketCountRow(
                bucketStart(rs.getObject("bucket_start", OffsetDateTime.class)), rs.getLong("cnt")),
        interval,
        timestamp(from),
        timestamp(to));
  }

  /**
   * Still-pending payments per bucket, split by age at query time.
   *
   * <p>{@code staleBefore} is the 24-hour cutoff: a row created before it has been pending too long
   * to be normal. Nothing in this codebase expires or reconciles a pending payment, so the stale
   * share is money that tried and never arrived.
   */
  public List<PendingBucketRow> findPendingPerBucket(
      Instant from, Instant to, Instant staleBefore, String interval) {
    return jdbcTemplate.query(
        "SELECT date_trunc(cast(? AS text), l.created_at, 'UTC') AS bucket_start,"
            + "       count(*) FILTER (WHERE l.created_at >= ?) AS fresh,"
            + "       count(*) FILTER (WHERE l.created_at < ?) AS stale"
            + "  FROM payment_ledger l"
            + " WHERE l.status = 'pending' AND l.created_at >= ? AND l.created_at < ?"
            + " GROUP BY bucket_start",
        (rs, rowNum) ->
            new PendingBucketRow(
                bucketStart(rs.getObject("bucket_start", OffsetDateTime.class)),
                rs.getLong("fresh"),
                rs.getLong("stale")),
        interval,
        timestamp(staleBefore),
        timestamp(staleBefore),
        timestamp(from),
        timestamp(to));
  }

  /** New non-deleted accounts per bucket. */
  public List<BucketCountRow> findNewUsersPerBucket(Instant from, Instant to, String interval) {
    return jdbcTemplate.query(
        "SELECT date_trunc(cast(? AS text), u.created_at, 'UTC') AS bucket_start, count(*) AS cnt"
            + "  FROM \"user\" u"
            + " WHERE u.deleted_at IS NULL AND u.created_at >= ? AND u.created_at < ?"
            + " GROUP BY bucket_start",
        (rs, rowNum) ->
            new BucketCountRow(
                bucketStart(rs.getObject("bucket_start", OffsetDateTime.class)), rs.getLong("cnt")),
        interval,
        timestamp(from),
        timestamp(to));
  }

  /**
   * Distinct members whose <em>first</em> succeeded payment landed in each bucket.
   *
   * <p>Distinct because one member may hold several acquisitions. The {@code NOT EXISTS} clause is
   * what makes this "first time" rather than "paid at all": a member who already paid before this
   * row's {@code paid_at} is excluded, so a repeat buyer is reported once, in the bucket where they
   * converted. Without it the series counts every payer active in the bucket, which cannot be
   * divided by signups to produce a conversion rate — the two would be different populations and
   * the ratio could exceed 1.
   */
  public List<BucketCountRow> findNewPayingUsersPerBucket(
      Instant from, Instant to, String interval) {
    return jdbcTemplate.query(
        "SELECT date_trunc(cast(? AS text), l.paid_at, 'UTC') AS bucket_start,"
            + "       count(DISTINCT l.user_id) AS cnt"
            + "  FROM payment_ledger l"
            + " WHERE l.status = 'succeeded' AND l.paid_at >= ? AND l.paid_at < ?"
            + "   AND NOT EXISTS ("
            + "         SELECT 1 FROM payment_ledger p"
            + "          WHERE p.user_id = l.user_id"
            + "            AND p.status = 'succeeded'"
            + "            AND p.paid_at < l.paid_at)"
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

  /** Succeeded revenue in one bucket. */
  public record RevenueRow(Instant bucketStart, long amount, long transactions) {}

  /** One plan's succeeded revenue inside the window. */
  public record PlanRevenueRow(
      UUID planId, String code, String name, long amount, long transactions, boolean active) {}

  /** A plain count in one bucket. */
  public record BucketCountRow(Instant bucketStart, long count) {}

  /** Pending payments in one bucket, split by whether they are already too old. */
  public record PendingBucketRow(Instant bucketStart, long fresh, long stale) {}
}
