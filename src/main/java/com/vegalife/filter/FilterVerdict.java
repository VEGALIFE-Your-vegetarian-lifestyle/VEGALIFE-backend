package com.vegalife.filter;

import com.vegalife.enums.post.PostFlag;
import java.util.List;
import java.util.Objects;

/**
 * Result of one filter run: the resulting post flag (never {@link PostFlag#PENDING} — PENDING is
 * assigned at enqueue time), the relevance score in [0, 1], and the reason codes carried into the
 * WARN/ERROR logs (FR-009).
 *
 * @param flag resulting flag: PASSED, REJECTED or NEEDS_REVIEW
 * @param score relevance score in [0, 1]
 * @param reasons reason codes, empty when the content passed
 */
public record FilterVerdict(PostFlag flag, double score, List<String> reasons) {

  public FilterVerdict {
    Objects.requireNonNull(flag, "flag must not be null");
    Objects.requireNonNull(reasons, "reasons must not be null");
    if (flag == PostFlag.PENDING) {
      throw new IllegalArgumentException("A filter run never produces PENDING");
    }
    reasons = List.copyOf(reasons);
  }
}
