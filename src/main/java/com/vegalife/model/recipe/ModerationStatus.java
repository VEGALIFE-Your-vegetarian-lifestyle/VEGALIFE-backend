package com.vegalife.model.recipe;

/**
 * Review state of a globally shared name — a dish, an ingredient, or a recipe (ADR-016).
 *
 * <p>These rows have no owner: a member creating a recipe introduces a dish and its ingredients on
 * the fly, and those names are then available to every other member's recipes and to the meal
 * planner. The status is therefore platform-wide, not per-post, and the question it answers is "may
 * this be shown to anyone?".
 *
 * <p>{@link #REJECTED} is a permanent, non-destructive block: the row is kept so existing
 * references keep resolving, but nobody can create the same name again (BR-DISH-001).
 */
public enum ModerationStatus {

  /** Introduced by a member and not yet reviewed. Invisible in public listings. */
  PENDING,

  /** Reviewed and accepted. Visible to everyone. */
  APPROVED,

  /** Reviewed and refused. Invisible, and permanently blocked from re-creation. */
  REJECTED
}
