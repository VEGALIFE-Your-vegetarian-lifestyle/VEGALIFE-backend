package com.vegalife.enums.post;

/**
 * Where a post sits in its publication lifecycle (ADR-023).
 *
 * <ul>
 *   <li>{@code drafted} — no publish intent, or withdrawn by the author.
 *   <li>{@code processing} — submitted; a verdict is outstanding, or the reason it is held is
 *       recorded in {@link PostFlag}.
 *   <li>{@code published} — the content filter passed it, or an admin approved it.
 *   <li>{@code unpublished} — an admin rejected it. The only terminal refusal.
 * </ul>
 *
 * <p>{@code status} is the only visibility gate: the feed and every read surface select {@code
 * published}. A {@link PostFlag} is not a second gate — it is the last verdict, kept after the post
 * leaves the review queue (ADR-025). Only {@code drafted} has none.
 */
public enum PostStatus {
  drafted,
  processing,
  published,
  unpublished
}
