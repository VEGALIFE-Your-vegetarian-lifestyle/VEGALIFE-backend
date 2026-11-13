package com.vegalife.enums.post;

/**
 * The verdict of the last moderation decision on a post, and — when it blocked — why (ADR-023,
 * ADR-025).
 *
 * <p>This is deliberately <em>not</em> the filter's verdict vocabulary: {@link
 * com.vegalife.service.filter.FilterOutcome} is the internal type, and the adapter maps it onto
 * these values. "A run is in flight" is {@code status = processing} with a null flag, not a value
 * here.
 *
 * <p>Publishing does not erase the verdict. A published post carries {@link #FILTER_PASSED} when
 * the filter cleared it, or the blocker it was held by when an admin overrode the filter — the two
 * are different facts and the API reports them as such. Rejection keeps the blocker too. Only a
 * {@code drafted} post has none, which is what the database enforces.
 */
public enum PostFlag {
  /** The filter cleared the content and published it. */
  FILTER_PASSED,
  /** The filter refused the content, or was unsure. Either way a human decides. */
  FILTER_FAILED,
  /** A near-duplicate of a published post; the target is recorded in {@code flag_detail}. */
  SIMILAR_CONTENT,
  /** The filter could not run (disabled, permanent provider failure, abandoned, or swept). */
  FILTER_SKIPPED
}
