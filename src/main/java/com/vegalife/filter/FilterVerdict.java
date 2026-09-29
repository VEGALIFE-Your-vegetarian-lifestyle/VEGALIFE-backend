package com.vegalife.filter;

import com.vegalife.model.post.Post;
import java.util.List;
import java.util.Objects;

/**
 * Result of one filter run: the resulting post flag (never {@link Post.Flag#PENDING} — PENDING is
 * assigned at enqueue time), the relevance score ({@code null} when a static rule rejected before
 * any embedding was produced), and the reason codes for logging and the audit trail (FR-009,
 * FR-010).
 *
 * @param flag resulting flag: PASSED, REJECTED or NEEDS_REVIEW
 * @param score relevance score in [0, 1], {@code null} for static rejections
 * @param reasons reason codes, empty when the content passed
 */
public record FilterVerdict(Post.Flag flag, Double score, List<String> reasons) {

  public FilterVerdict {
    Objects.requireNonNull(flag, "flag must not be null");
    Objects.requireNonNull(reasons, "reasons must not be null");
    if (flag == Post.Flag.PENDING) {
      throw new IllegalArgumentException("A filter run never produces PENDING");
    }
    reasons = List.copyOf(reasons);
  }
}
