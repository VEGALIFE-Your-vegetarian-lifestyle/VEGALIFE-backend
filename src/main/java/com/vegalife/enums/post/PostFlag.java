package com.vegalife.enums.post;

/**
 * Why a post is held at {@link PostStatus#processing} (ADR-023).
 *
 * <p>This is deliberately <em>not</em> the filter's verdict vocabulary: a passing verdict publishes
 * the post and clears the flag, and "a run is in flight" is {@code status = processing} with a null
 * flag. The enum therefore contains only the reasons a post may be blocked, and the database
 * enforces that a flag can only exist while the post is {@code processing}.
 *
 * <p>{@link com.vegalife.service.filter.FilterOutcome} is the internal verdict type; the adapter
 * maps it onto these values.
 */
public enum PostFlag {
  /** The filter refused the content, or was unsure. Either way a human decides. */
  FILTER_FAILED,
  /** A near-duplicate of a published post; the target is recorded in {@code flag_detail}. */
  SIMILAR_CONTENT,
  /** The filter could not run (disabled, permanent provider failure, abandoned, or swept). */
  FILTER_SKIPPED
}
