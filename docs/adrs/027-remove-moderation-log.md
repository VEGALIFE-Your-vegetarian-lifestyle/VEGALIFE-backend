# ADR-027: Remove the `moderation_log` Audit Table

## Status

Accepted

## Date

2026-11-19

## Deciders

Product owner (scope call: no audit trail for now), backend team (implementation and consequences
review).

## Context

`moderation_log` was created by `V18` to satisfy
[BR-ADMIN-002](../brs/posts.md#business-rule-every-administrator-content-moderation-action-is-logged):
every administrator action over content someone else owns was appended as a row naming the actor
(`actor_id`), the action (`CREATE_DISH`, `APPROVE_POST`, `DELETE_COMMENT`, `UPDATE_SETTING`, …), the
target (`target_type` + `target_id`), the time, and — after `V32` — an optional `reason`.

Five facts about how it was used, verified in the code, made it dead weight:

1. **Nothing read it.** No controller, service or repository method returned a row.
   `ModerationLogRepository` extended `JpaRepository` and declared no query of its own, and no admin
   API surfaced the table. The only way to see a row was ad-hoc SQL against the database — a
   capability the deployed product did not offer. It was write-only.
2. **It cost a write on every moderation path.** Ten services built and persisted a row inside the
   same transaction as the change they were recording. The cost was small per action but paid
   everywhere, including paths that only touched a platform setting.
3. **Its `reason` overlapped a real, readable column.** For posts and recipes the reason was stored
   twice: on `moderation_log.reason` (never read) and on `post.rejection_reason` /
   `recipe.rejection_reason` (`V51`), which *is* surfaced to the author. Only the second copy did
   any work.
4. **It was the sole home of one thing nobody needed.** A content-filter outage wrote an
   `action = FILTER_UNAVAILABLE` row with a NULL actor and the classified cause in `reason`. That
   was the only place the classification existed — reachable only by SQL.
5. **Keeping it implied a capability the product does not have.** A table in the schema named
   "moderation log" reads as a promise of an audit view. With no reader, that promise was never
   kept, and every new moderation path had to remember to write to it.

The product owner has decided the platform does not keep an audit trail for now.

## Decision

Drop `moderation_log` and every writer of it. Administrator moderation actions are no longer recorded
anywhere: the acting admin, the action, and any rationale beyond a post/recipe rejection reason are
not persisted.

What stays, deliberately:

- `post.rejection_reason` and `recipe.rejection_reason`. These are **current state**, not history:
  they answer "why is this rejected right now", are surfaced to the author through the admin and
  recipe responses, and are cleared when the row is approved again. Only the log-only `reason`
  fields (dish, ingredient, comment, setting, post approval) die with the table.
- Every moderation **state** column (`post.status`, `post.flag`, `post.flag_detail`,
  `recipe.moderation_status`, `dish.moderation_status`, `ingredient.moderation_status`,
  `comment.deleted_at`, `platform_setting.updated_by`). Approval, rejection, deletion and settings
  writes are unchanged in behaviour.
- The `adminId` parameter threaded through the admin services, which still feeds the operational
  `log.info` line on each action. Application logs are not an audit trail — they are not queryable
  and are not retained to a policy — but they keep a developer's ability to trace a request.

Schema change is a single forward-only migration, `V60__drop_moderation_log.sql`. `V18` and `V32`
are never edited: Flyway checksums forbid it, and rewriting history would not remove rows from a
database that already ran them.

The API surface shrinks where the body existed only to supply a log `reason`:

- `POST /api/admin/posts/{postId}/approve` — body-less (was optional `reason`).
- `POST /api/admin/recipes/{recipeId}/approve` — body-less.
- `POST /api/admin/dishes/{dishId}/approve|reject`, `POST /api/admin/ingredients/{ingredientId}/approve|reject`
  — body-less.
- `POST /api/admin/comments/{commentId}/moderate` — body-less (was a **required** `reason`).

`POST /api/admin/posts/{postId}/reject` and `POST /api/admin/recipes/{recipeId}/reject` keep their
optional `reason`, because it writes `rejection_reason`. No route is removed; no new route is added,
since no read endpoint was ever wanted.

Clients that still send the deleted fields are not broken: Spring Boot leaves
`FAIL_ON_UNKNOWN_PROPERTIES` disabled and the project adds no Jackson configuration overriding it, so
an unknown `reason` on a now-body-less endpoint is discarded rather than rejected.

## Considered options

- **Option A — keep the table and add an admin read API.** Rejected: nobody asked for an audit view,
  and it is the expensive half of the feature (pagination, filtering, retention, access control) for
  a capability the product owner does not want.
- **Option B — keep writing, stop reading.** Rejected: that is exactly the state this ADR ends.
  Rows accumulate with no way to reach them, and each new moderation path keeps paying the write.
- **Option C — keep the table but drop the unused `reason` column only.** Rejected: the larger cost
  and the misleading name belong to the table, not the column, and a table with no reader is not
  made honest by narrowing it.
- **Option D — drop the table and every writer; keep the rejection-reason columns.** Chosen. It
  leaves the product with exactly one answer to "why was this rejected" — the one users can read —
  and one less thing that implies a capability that does not exist.
- **Option E — drop the table but keep the writers writing to the application log.** Rejected as a
  scope decision: `log.info` already exists on these paths for operability, and enriching it into a
  pseudo-audit trail would recreate the same problem outside the database.

## Consequences

**Positive:**

- Each moderation action loses a database write, and the ten services stop carrying a repository
  dependency they only used to satisfy an unread table.
- The schema stops advertising an audit capability the product does not have.
- The reason a user is told and the reason the system stores are now one value in one place
  (`rejection_reason`), so they cannot disagree.
- Approval payloads disappear where they only carried a rationale, which simplifies those contracts.

**Negative / trade-offs:**

- **Administrator actions are no longer attributable after the fact.** "Who unpublished this post?"
  and "who approved this recipe?" now have no answer beyond application logs. Investigating abuse by
  an admin becomes impossible in principle rather than merely inconvenient.
- **The classified cause of a content-filter outage is lost.** A `FILTER_UNAVAILABLE` reason
  (provider 429 vs. bad key vs. timeout) was only ever written here. The post still settles at
  `FILTER_SKIPPED` with cause `UNAVAILABLE`, so the review queue is correct; only the provider
  detail is gone, and an operator must now read the application log to get it.
- **Settings changes lose their history.** `platform_setting.updated_by` still names the admin who
  last touched a row, but the previous value and the sequence of changes are gone.
- **Dropping the table is irreversible.** Every row is discarded, not migrated. Recovering history
  would require the database backup, not a code change; re-creating the table would start empty.
- **`BR-ADMIN-002` is retired**, so any doc or test that still asserts a log row is wrong and had to
  be updated in the same change.

## Verification

Revisit if either becomes true:

- A requirement appears to attribute administrator actions — a moderation dispute, a compliance
  obligation, or an incident review that has to answer "who did this". That re-opens the question at
  the level of *what record and who reads it*, not "restore `moderation_log`", because the old table
  had no reader.
- Content-filter outages become frequent enough that the classified cause needs to be queryable
  rather than grep-able in the application log. That is a narrower need than a full moderation audit
  trail and would probably be a purpose-built filter-failure table, not a resurrection of this one.
