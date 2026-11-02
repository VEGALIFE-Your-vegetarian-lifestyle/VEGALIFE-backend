# ADR-016: Moderate Components Without Per-Occurrence Verdict Storage

## Status

Accepted — 2026-10-23.

## Context

Post content filtering ([ADR-007](007-post-content-filtering.md)) scores one
string per post: `title + content`. A post may also attach a recipe, and every
recipe references a dish and a set of ingredients. Recipes carry free-text
`instructions`; dishes and ingredients carry user-supplied names. None of it was
examined.

The reviewer's ask was to verify every component of a post, and to let admins
approve dishes and ingredients that users create. The second half matters because
dishes and ingredients are **global rows with no owner**: `RecipeService`
creates them on demand, and a menu references a dish directly
(`menu_detail.dish_id NOT NULL`), independent of any post.

Three designs were considered for tracking per-component state.

### Option A — a per-component verdict table

One row per (post, component) capturing score, threshold, model and a
`component_ref` to the dish/ingredient/recipe. This was the original proposal in
the design discussion.

It was rejected as over-engineered. Each column was justified by a need that
turned out to be already satisfied:

- "which component is new" is `moderation_status = 'PENDING'` on the row;
- "which component and which id" is the row itself;
- "which rows did this post introduce" was proposed to implement "reject the new
  ones, leave approved ones alone" — but at rejection time the only question is
  *"is this approved right now?"*, which is also `moderation_status`. The extra
  flag would only matter for a row that was pending at creation and approved
  before rejection, and in that case the row **is** approved, so leaving it alone
  is already correct.

The table would therefore have stored, permanently, data that a join produces on
demand.

### Option B — columns on the component rows

`moderation_status` on `dish`, `ingredient` and `recipe`. This expresses the
question the rest of the system actually asks: *may this name be shown to
anyone?* It is global, survives across posts, and is what `menu_detail` needs.

### Option C — scoring short names with their own centroids

Rejected on capability grounds. A centroid answers "is this text about vegan
food", not "is this name acceptable". `"Bún bò Huế chay"` may score low for being
unfamiliar while legitimate; `"Thịt chó"` is on-topic as food and would score
well despite being unacceptable. With thousands of legitimate ingredients, a
centroid fitted to a small corpus misfires on exactly the long tail it is meant
to police.

## Decision

**Adopt Option B, reject A and C.**

1. `moderation_status` (`PENDING`/`APPROVED`/`REJECTED`) on `dish`, `ingredient`
   and `recipe`. Existing rows backfilled to `APPROVED` in the same migration.
2. Score exactly two components: `CONTENT` (`title + content`) and `RECIPE`
   (`name + description + instructions`). Store their scores on `post` and
   `recipe` respectively.
3. Dish and ingredient names are **not** scored. Without admin approval a new
   name is not visible, so approval — not an automatic check — provides the
   guarantee.
4. A post is forwarded to review if content or recipe scores below threshold, or
   any referenced name is `PENDING`.
5. Admin component detail is derived at read time, never stored.

## Consequences

**Positive.**

- No permanent per-occurrence history to reconcile, expire or migrate.
- Two embedding calls per publish with a recipe, one without — down from four in
  the rejected design.
- Name risk is handled where it can actually be handled: by a human, at
  approval.
- `menu_detail` and the meal planner keep working because visibility lives on the
  dish row, not on a post-scoped verdict.

**Negative.**

- **Names are global.** If user A introduces a pending name and user B reuses it
  in their own post, rejecting A's post rejects the name for B too. Both posts
  are pending, so no published content breaks, but the coupling exists. It
  follows from dishes and ingredients being shared, which the product owner
  confirmed is desired.
- **A queued post cannot explain a component's history.** The admin sees the
  current score and current pending names, not how they were arrived at. The
  rejected design would have kept that. Accepted: the detail endpoint shows the
  scored text, which is what the admin judges.
- **Recipe text needs its own calibration.** One more centroid pair and corpus,
  documented in [the calibration spec](../feats/filter-calibration-gemini.md).
- **Members lose the ability to fix their own recipes** if recipe update is
  admin-only, which keeps moderation unbypassable but is inconvenient.

## Alternatives considered

- **Scoring names with centroids** — see Option C.
- **A blocklist for names** — rejected as the primary mechanism because it can
  only prove a name is *not on a list*, not that it is a real ingredient. It
  cannot scale to the long tail and gives false confidence.
- **Eager approval of names reused by later posts** — this would reintroduce the
  "approved rows keep flagging posts" problem the owner explicitly rejected.

## Related

- [ADR-007: Filter post content before publication](007-post-content-filtering.md)
- [ADR-011: Keep post visibility in `status` and the verdict in `flag`](011-post-status-and-filter-axis-separation.md)
- [ADR-014: Serve filter embeddings from Gemini](014-embedding-provider-gemini.md)
- [Feature spec: component-level verification](../feats/component-level-verification.md)
