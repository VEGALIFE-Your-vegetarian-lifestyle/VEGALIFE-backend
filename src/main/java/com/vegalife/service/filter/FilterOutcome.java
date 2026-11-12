package com.vegalife.service.filter;

/**
 * What one content-filter run decided (ADR-023).
 *
 * <p>Separate from {@link com.vegalife.enums.post.PostFlag} on purpose: a verdict is an internal
 * observation, while a flag is a persisted reason a post is held. {@code PASS} never becomes a flag
 * — it publishes the post and clears one — so keeping them in one enum would let code store a value
 * the column can never legally hold.
 */
public enum FilterOutcome {
  /** The content cleared the accept threshold. */
  PASS,
  /** The content fell below the reject threshold. */
  FAIL,
  /** The score fell between the two bands: neither a pass nor a refusal a human cannot review. */
  UNCERTAIN
}
