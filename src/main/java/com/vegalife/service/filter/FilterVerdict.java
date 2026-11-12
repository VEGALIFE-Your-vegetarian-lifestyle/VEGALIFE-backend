package com.vegalife.service.filter;

import java.util.List;
import java.util.Objects;

/**
 * Result of one filter run: what the model decided, the relevance score in [0, 1], and the reason
 * codes carried into the WARN/ERROR logs (FR-009, ADR-023).
 *
 * <p>The outcome is deliberately not a {@link com.vegalife.enums.post.PostFlag}: a passing run
 * publishes the post and clears the flag rather than storing a value, so the two vocabularies must
 * not be interchangeable.
 *
 * @param outcome what this run decided
 * @param score relevance score in [0, 1]
 * @param reasons reason codes, empty when the content passed
 */
public record FilterVerdict(FilterOutcome outcome, double score, List<String> reasons) {

  public FilterVerdict {
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(reasons, "reasons must not be null");
    reasons = List.copyOf(reasons);
  }
}
