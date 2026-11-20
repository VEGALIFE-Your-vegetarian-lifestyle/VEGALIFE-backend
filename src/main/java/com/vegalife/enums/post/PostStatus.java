package com.vegalife.enums.post;

/**
 * Where a post sits in its publication lifecycle (ADR-023, ADR-029).
 *
 * <ul>
 *   <li>{@code drafted} — no publish intent, or withdrawn by the author. Carries no flag.
 *   <li>{@code processing} — submitted; a filter run is outstanding. Carries no flag: the verdict
 *       does not exist yet.
 *   <li>{@code pending} — a verdict was recorded and an admin has not decided. Carries the verdict
 *       in {@link PostFlag}. A media-bearing post the filter passed lands here instead of {@code
 *       published}, because the filter only reads text (ADR-029).
 *   <li>{@code published} — the filter passed it and it carried no media, or an admin approved it.
 *   <li>{@code unpublished} — an admin rejected it. The only terminal refusal.
 * </ul>
 *
 * <p>{@code status} is the only visibility gate: the feed and every read surface select {@code
 * published}. A {@link PostFlag} is not a second gate — it is the last verdict, kept after the post
 * leaves the review queue (ADR-025). Only {@code drafted} has none, and {@code processing} has none
 * because its verdict has not been reached.
 */
public enum PostStatus {
  drafted,
  processing,
  pending,
  published,
  unpublished
}
