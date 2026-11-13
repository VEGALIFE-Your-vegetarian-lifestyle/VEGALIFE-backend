package com.vegalife.service.filter;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vegalife.enums.post.PostFlag;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Builds the JSON snapshot stored in {@code post.flag_detail} (ADR-024, ADR-025).
 *
 * <p>One object per verdict, always carrying {@code kind} so the snapshot and the flag it qualifies
 * can never describe different things — the database enforces {@code flag_detail->>'kind' = flag}.
 * Everything else is the evidence that verdict actually owns:
 *
 * <ul>
 *   <li>{@code FILTER_PASSED} — the score and the accept boundary it cleared. Publication keeps the
 *       snapshot, so the row still explains itself after the post leaves the review queue.
 *   <li>{@code FILTER_FAILED} — the outcome, the score, and the accept/reject boundary that was in
 *       force for that run. The boundary is snapshotted because both thresholds are live platform
 *       settings: a score without the bar it was measured against stops explaining itself the
 *       moment an administrator retunes the filter.
 *   <li>{@code SIMILAR_CONTENT} — the post it resembled, how similar (cosine similarity in [0,1]),
 *       and the duplicate threshold that was applied.
 *   <li>{@code FILTER_SKIPPED} — a cause code only. The prose belongs in {@code moderation_log},
 *       which is append-only and already records it; a free-text copy on the row was what used to
 *       be truncated.
 * </ul>
 *
 * <p>Scores are rounded to four decimals so a snapshot reads the same as the audit log and does not
 * carry floating-point noise into the API.
 */
public final class PostFlagDetail {

  public static final String CAUSE_DISABLED = "DISABLED";
  public static final String CAUSE_SWEPT = "SWEPT";
  public static final String CAUSE_UNAVAILABLE = "UNAVAILABLE";

  private static final int SCALE = 4;

  private PostFlagDetail() {}

  /**
   * Snapshot for a verdict that published the post. There is no {@code outcome}: the kind already
   * says the run passed, and repeating it would let the two disagree.
   */
  public static ObjectNode passed(FilterVerdict verdict) {
    ObjectNode node = base(PostFlag.FILTER_PASSED);
    node.put("score", round(verdict.score()));
    if (verdict.threshold() != null) {
      node.put("threshold", round(verdict.threshold()));
    }
    return node;
  }

  /** Snapshot for a refused or uncertain content verdict. */
  public static ObjectNode filterFailed(FilterVerdict verdict) {
    ObjectNode node = base(PostFlag.FILTER_FAILED);
    node.put("outcome", verdict.outcome().name());
    node.put("score", round(verdict.score()));
    if (verdict.threshold() != null) {
      node.put("threshold", round(verdict.threshold()));
    }
    return node;
  }

  /** Snapshot for a post held because it resembles an already-published one. */
  public static ObjectNode similarContent(UUID postId, double similarity, double threshold) {
    ObjectNode node = base(PostFlag.SIMILAR_CONTENT);
    node.put("postId", postId.toString());
    node.put("similarity", round(similarity));
    node.put("threshold", round(threshold));
    return node;
  }

  /** Snapshot for a post the filter never judged. */
  public static ObjectNode skipped(String cause) {
    ObjectNode node = base(PostFlag.FILTER_SKIPPED);
    node.put("cause", cause);
    return node;
  }

  private static ObjectNode base(PostFlag flag) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("kind", flag.name());
    return node;
  }

  private static double round(double value) {
    return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
  }
}
