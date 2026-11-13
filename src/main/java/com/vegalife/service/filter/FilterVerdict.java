package com.vegalife.service.filter;

import java.util.List;
import java.util.Objects;

/**
 * Result of one filter run: what the model decided, the relevance score in [0, 1], the boundary
 * that decision was measured against, and the reason codes carried into the WARN/ERROR logs
 * (FR-009, ADR-023, ADR-024).
 *
 * <p>The outcome is deliberately not a {@link com.vegalife.enums.post.PostFlag}: the mapping is not
 * one-to-one — {@code FAIL} and {@code UNCERTAIN} both become {@code FILTER_FAILED} — so the two
 * vocabularies must not be interchangeable.
 *
 * @param outcome what this run decided
 * @param score relevance score in [0, 1]
 * @param threshold the accept/reject boundary in force for this run — the bar the score failed to
 *     clear, or the accept boundary when it cleared one; {@code null} when the caller did not know
 *     it (a component escalation of a synthetic verdict, or a test double)
 * @param reasons reason codes, empty when the content passed
 */
public record FilterVerdict(
    FilterOutcome outcome, double score, Double threshold, List<String> reasons) {

  public FilterVerdict {
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(reasons, "reasons must not be null");
    reasons = List.copyOf(reasons);
  }

  /** Convenience for callers that do not know which boundary was applied. */
  public FilterVerdict(FilterOutcome outcome, double score, List<String> reasons) {
    this(outcome, score, null, reasons);
  }
}
