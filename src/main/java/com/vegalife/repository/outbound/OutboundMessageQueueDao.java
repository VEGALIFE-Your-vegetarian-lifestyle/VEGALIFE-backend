package com.vegalife.repository.outbound;

import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raw-SQL access to the outbound queue: atomic claim (FOR UPDATE SKIP LOCKED + RETURNING) and
 * guarded state transitions that only succeed for the current owner of a PROCESSING row (ADR-005).
 */
@Repository
public class OutboundMessageQueueDao {

  private static final String CLAIM_SQL =
      "UPDATE outbound_message"
          + " SET status = 'PROCESSING', attempts = attempts + 1,"
          + " locked_at = ?, locked_by = ?"
          + " WHERE id IN ("
          + "   SELECT id FROM outbound_message"
          + "   WHERE status IN ('PENDING', 'DEFERRED') AND next_attempt_at <= ?"
          + "   ORDER BY next_attempt_at"
          + "   FOR UPDATE SKIP LOCKED"
          + "   LIMIT ?)"
          + " RETURNING *";

  private static final String COMPLETE_SQL =
      "UPDATE outbound_message"
          + " SET status = 'COMPLETED', completed_at = ?, payload = NULL,"
          + " locked_at = NULL, locked_by = NULL"
          + " WHERE id = ? AND status = 'PROCESSING' AND locked_by = ?";

  private static final String TERMINAL_SQL =
      "UPDATE outbound_message"
          + " SET status = ?, payload = NULL, locked_at = NULL, locked_by = NULL"
          + " WHERE id = ? AND status = 'PROCESSING' AND locked_by = ?";

  private static final String SCHEDULE_RETRY_SQL =
      "UPDATE outbound_message"
          + " SET status = ?, next_attempt_at = ?, locked_at = NULL, locked_by = NULL"
          + " WHERE id = ? AND status = 'PROCESSING' AND locked_by = ?";

  private static final String RECLAIM_STALE_SQL =
      "UPDATE outbound_message"
          + " SET status = CASE WHEN attempts < ? THEN 'PENDING' ELSE 'DEFERRED' END,"
          + " next_attempt_at = CASE WHEN attempts < ? THEN next_attempt_at ELSE ? END,"
          + " locked_at = NULL, locked_by = NULL"
          + " WHERE status = 'PROCESSING' AND locked_at < ?";

  private static final RowMapper<OutboundMessage> ROW_MAPPER = OutboundMessageQueueDao::mapRow;

  private final JdbcTemplate jdbcTemplate;

  public OutboundMessageQueueDao(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Atomically claims up to {@code batchSize} due rows: flips them to PROCESSING, increments
   * attempts, and records the worker id. The claim runs in its own short transaction so locks are
   * released before any SMTP I/O happens.
   */
  @Transactional
  public List<OutboundMessage> claimDue(Instant now, String workerId, int batchSize) {
    OffsetDateTime due = dbTime(now);
    return jdbcTemplate.query(CLAIM_SQL, ROW_MAPPER, due, workerId, due, batchSize);
  }

  /** Marks a claimed row delivered; fails (returns false) if the caller no longer owns it. */
  @Transactional
  public boolean markCompleted(UUID id, String workerId, Instant now) {
    return jdbcTemplate.update(COMPLETE_SQL, dbTime(now), id, workerId) == 1;
  }

  /**
   * Marks a claimed row FAILED or EXPIRED and clears the payload; fails (returns false) if the
   * caller no longer owns it.
   */
  @Transactional
  public boolean markTerminal(UUID id, OutboundStatus terminalStatus, String workerId) {
    requireTerminal(terminalStatus);
    return jdbcTemplate.update(TERMINAL_SQL, terminalStatus.name(), id, workerId) == 1;
  }

  /**
   * Reschedules a claimed row for a later attempt (PENDING for fast retries, DEFERRED after the
   * fast-retry window); fails (returns false) if the caller no longer owns it.
   */
  @Transactional
  public boolean scheduleRetry(
      UUID id, OutboundStatus retryStatus, Instant nextAttemptAt, String workerId) {
    requireRetryStatus(retryStatus);
    return jdbcTemplate.update(
            SCHEDULE_RETRY_SQL, retryStatus.name(), dbTime(nextAttemptAt), id, workerId)
        == 1;
  }

  /**
   * Reclaims rows stuck in PROCESSING past the visibility timeout: back to PENDING while within the
   * fast-retry window ({@code attempts < fastAttemptLimit}), otherwise DEFERRED at {@code
   * deferredAt}. Returns the number of reclaimed rows.
   */
  @Transactional
  public int reclaimStale(Instant cutoff, int fastAttemptLimit, Instant deferredAt) {
    return jdbcTemplate.update(
        RECLAIM_STALE_SQL, fastAttemptLimit, fastAttemptLimit, dbTime(deferredAt), dbTime(cutoff));
  }

  private static OffsetDateTime dbTime(Instant instant) {
    return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static Instant readTime(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private static void requireTerminal(OutboundStatus status) {
    if (status != OutboundStatus.FAILED && status != OutboundStatus.EXPIRED) {
      throw new IllegalArgumentException("Not a terminal status: " + status);
    }
  }

  private static void requireRetryStatus(OutboundStatus status) {
    if (status != OutboundStatus.PENDING && status != OutboundStatus.DEFERRED) {
      throw new IllegalArgumentException("Not a retry status: " + status);
    }
  }

  private static OutboundMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
    return OutboundMessage.builder()
        .id(rs.getObject("id", UUID.class))
        .channel(OutboundChannel.valueOf(rs.getString("channel")))
        .recipient(rs.getString("recipient"))
        .payload(rs.getString("payload"))
        .status(OutboundStatus.valueOf(rs.getString("status")))
        .attempts(rs.getInt("attempts"))
        .nextAttemptAt(readTime(rs, "next_attempt_at"))
        .expiresAt(readTime(rs, "expires_at"))
        .lockedAt(readTime(rs, "locked_at"))
        .lockedBy(rs.getString("locked_by"))
        .completedAt(readTime(rs, "completed_at"))
        .createdAt(readTime(rs, "created_at"))
        .build();
  }
}
