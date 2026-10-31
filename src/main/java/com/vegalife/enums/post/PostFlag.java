package com.vegalife.enums.post;

public enum PostFlag {
  PENDING,
  PASSED,
  REJECTED,
  NEEDS_REVIEW,
  /** Near-duplicate of an existing published post; always routed to admin, never auto-rejected. */
  DUPLICATE
}
