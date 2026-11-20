package com.vegalife.repository;

import java.util.Locale;

/**
 * Builds the {@code LIKE} operand used by every keyword filter (BR-POST-014, BR-RECP-009).
 *
 * <p>One implementation on purpose: the escape rule is a correctness property, not a formatting
 * detail. If one listing escaped and another did not, a search for {@code %} would quietly mean
 * "everything" on that route only. Lives in the repository root package so each entity's
 * specification package — the same rank, ADR-018 — can import it without a layering edge.
 */
public final class LikePatterns {

  /** Escape character for user-supplied {@code LIKE} patterns. */
  public static final char ESCAPE = '\\';

  private LikePatterns() {}

  /**
   * A {@code %…%} operand matching {@code keyword} as a case-insensitive whole phrase. {@code %},
   * {@code _} and {@code \} are escaped so they match literally; the caller must pass {@link
   * #ESCAPE} to the {@code like(...)} call for the escapes to take effect.
   */
  public static String contains(String keyword) {
    String escaped =
        keyword
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
