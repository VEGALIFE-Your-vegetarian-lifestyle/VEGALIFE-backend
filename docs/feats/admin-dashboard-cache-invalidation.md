# Feature Spec: Admin Dashboard Cache Invalidation

## Status

Approved

## Author / owner

Backend team. Owner of the freshness contract: backend.

## Summary

Every write that changes a number on the admin dashboard now clears the cached aggregate it
invalidated, so an administrator who acts on the dashboard sees the effect immediately instead of
waiting for the cache to expire.

## Problem / motivation

`AdminDashboardService.getSummary()` and `getCharts()` are `@Cacheable` (Caffeine, 5 and 15 minute
TTLs) and until this change nothing in the codebase called `@CacheEvict` or cleared a cache on a
write path. Freshness was therefore TTL-only, and the dashboard was correct only as of the moment
its entry was built.

Concretely, before this change:

```
09:00:00  admin loads dashboard     -> 7 pending posts   (entry cached)
09:00:30  admin approves post #42   -> published, queue really has 6
09:00:31  admin loads dashboard     -> still 7           (cache hit, 5 minutes old)
09:05:00  TTL expires
09:05:01  admin loads dashboard     -> 6
```

This is worst exactly where it matters. `moderationQueue.pendingPosts` and `money.pendingStaleCount`
are the two values the dashboard exists to alert on, and an admin is usually looking at the dashboard
at the moment they take the action that changes it — so they watch the number refuse to move and
learn not to trust it.

## Goals

- A dashboard read issued after a relevant write commits reflects that write. No TTL wait.
- No eviction on writes that redisplay-only metrics depend on, so the cache stays warm where it is
  merely informative rather than actionable.
- Eviction must never fire for a write that rolled back.
- A dashboard load racing an eviction must not be able to re-cache pre-commit state.

## Non-goals

- **Not** making `oldestPendingAgeHours` or `pendingStaleCount` real-time. Both move on the clock,
  not on writes; eviction cannot help and they keep their TTL bound. See "Risks" below.
- **Not** evicting on `castVote`, `save*`/`unsave*`, `createComment`, `RequestAllowanceService.record`,
  `AuthService.register`, or `verifyEmail`. These are high-volume member writes backing
  redisplay-only metrics; evicting there would keep a 26-query summary permanently cold.
- **Not** shrinking the TTLs, adding Redis, or moving to a shared cache. This stays in-process
  Caffeine; a second instance's cache is not invalidated (see "Risks").
- **Not** changing any endpoint, response shape, or the `generatedAt` semantics.

## Requirements

### Functional Requirements

- [x] FR-001: Approving a post clears the summary and the charts caches.
- [x] FR-002: Rejecting a post clears the summary and the charts caches.
- [x] FR-003: Deleting a post (by its author or it being soft-deleted by an admin) clears both.
- [x] FR-004: Creating a post, editing a post, and attaching media to a post clear the summary; the
      two that can change a post's publication state clear both. Attaching *recipes* to a post does
      not evict: it writes the `post_recipe` join table, which no dashboard metric reads.
- [x] FR-005: The content-filter delivery path clears the caches on every settled outcome — publication
      clears both; a hold (`FILTER_FAILED`, `FILTER_SKIPPED`, component escalation, duplicate) or a
      filter-unavailable/abandoned message clears the summary.
- [x] FR-006: The hourly stale-pending sweep clears the summary, but only when it actually settled at
      least one post.
- [x] FR-007: Approving or rejecting a recipe, and soft-deleting a recipe by either an admin or its
      author, clears the summary.
- [x] FR-008: Creating a recipe clears the summary (it enters the queue as `PENDING`).
- [x] FR-009: Creating a comment does **not** evict; deleting a comment (author or admin) and
      reporting a comment do.
- [x] FR-010: Moderating a comment and suspending or restoring a user clear the summary.
- [x] FR-011: A settled payment (`fulfil`) clears both caches; a failure transition clears the summary.
- [x] FR-012: Claiming a free plan clears the summary.
- [x] FR-013: Creating, updating, or deactivating an AI plan clears the charts — `revenueByPlan`
      projects `p.active`, so an activation toggle changes the chart with no money moving.
- [x] FR-014: A rolled-back write evicts nothing.
- [x] FR-015: `castVote` and the save/unsave paths do not evict, verified by a test that asserts the
      entry is still present afterwards.

### Non-Functional Requirements

- [x] NFR-MAINT-001: Call sites are reachable from rank 5–7 only, and the invalidator lives in its own
      leaf package (`service/cache`) so no new package cycle is created (ADR-018). It imports only
      `service/admin/AdminDashboardCacheNames` and Spring Cache types.
- [x] NFR-MAINT-002: The write path stays a one-line call site (`dashboardCache.summary()` /
      `.charts()` / `.all()`), not an `@CacheEvict` annotation repeated on 20 methods, so the cache
      names and the eviction policy live in exactly one readable place.
- [x] NFR-MAINT-003: An eviction failure is surfaced, not swallowed — a silently stale dashboard is
      the defect this feature exists to remove.
- [x] NFR-SCALE-001: Eviction cost is bounded by intent: `summary()` drops one entry, `charts()` three
      (one per range), `all()` four. All are O(1) map removals.
- [x] NFR-PERF-001: No dashboard read becomes slower; `sync = true` additionally prevents a stampede
      of concurrent 26-query rebuilds on a cold cache.
- [x] NFR-SEC-001: No new endpoint and no new authority. The invalidator is internal and unreachable
      from HTTP.

## Design overview

`service/cache/AdminDashboardCacheInvalidator` exposes three intents:

| Method | Clears | Used when |
|---|---|---|
| `summary()` | `adminDashboardSummary` | A summary-only metric moved (queue counts, signups, votes-if-we-wanted, free grants) |
| `charts()` | `adminDashboardCharts` (all three range keys) | Revenue, purchases, or plan visibility changed |
| `all()` | both | One write moved both (publish/unpublish, payment success) |

**Deferred to `afterCommit`.** The invalidator registers a `TransactionSynchronization` and clears in
`afterCommit()`; with no active synchronization it clears immediately. Without this, a write evicts
while its transaction is still open, and a dashboard read that races the write can miss the cache,
query the database, observe pre-commit state, and re-populate the entry with stale data that then
survives the whole TTL. `@Cacheable(..., sync = true)` was added to both dashboard reads as the second
half of that guard: a load already in flight is not permitted to write back after the clear.

**Why not `@CacheEvict` annotations.** Most of the producers are private helpers Spring cannot advise
(`ContentFilterOutboundAdapter.apply`, `PaymentWebhookService.fulfil`, `PostService.markForAdminReview`,
`SubTask`…). An annotation on such a method compiles cleanly and silently does nothing. And
`RecipeService.createRecipe` opens its own transaction through `TransactionTemplate`, so a
transaction-synchronization registered inside it would fire after that inner commit with no outer
transaction to attach to.

**Placement (ADR-018).** The invalidator is imported by `service/post`, `service/recipe`,
`service/filter`, `service/payment`, `service/credit`, `service/subscription`, `service/admin`, and
`scheduled`. It cannot live in `service/admin`: `AdminRecipeService` already imports `service/recipe`,
so `service/recipe → service/admin` would close a cycle. ADR-018 accepts exactly one same-rank cycle
and states that a second one is the point at which the model should be re-derived rather than patched.
A leaf package (`service/cache`, still rank 5) avoids the question entirely.

## Success metrics

- Zero dashboard values that contradict the write visible in the admin's own recent action, measured
  by the integration test asserting the value flips within the same request cycle it was cached in.
- Summary evictions per hour should track admin actions, not member traffic. The `castVote` test is
  the guard: if vote/save eviction is ever added back, that test fails.

## Acceptance criteria

**As an** administrator, **I want** the dashboard to reflect an action I just took, **so that** I can
trust the queue counts and the money figures while I am working in the console.

- [x] Given a dashboard payload is cached showing 1 pending post, when an admin approves that post,
      then the next dashboard read reports 0 pending posts (no TTL wait).
- [x] Given the charts are cached, when a post is approved, then the published-posts series for the
      current range includes it on the next read.
- [x] Given a dashboard payload is cached, when an admin rejects a post or suspends a user, then the
      affected count is correct on the next read.
- [x] Given a cached summary, when a member casts a vote, then the entry is still cached (deliberate
      staleness bound, not an oversight).
- [x] Given a write that throws and rolls back, when the transaction completes, then no cache entry
      is cleared.
- [x] Given a transaction is open, when the invalidator is called, then nothing is cleared until
      `afterCommit` runs.

## Risks / open questions

- **Clock-driven metrics stay stale by design.** `oldestPendingAgeHours` grows every hour with no
  write at all, and `pendingStaleCount` counts pending ledgers that cross the 24-hour line on their
  own. Eviction addresses membership changes only; both keep their 5-minute TTL. If either needs to
  be exact, the fix is to remove it from the cached payload or shorten the summary TTL — a different
  change with a different cost.
- **Multi-instance deployments are not covered.** Caffeine is per-process, so with more than one
  backend instance an admin's write only clears the cache on the instance that served it; another
  instance can serve a stale payload for up to its TTL. This predates the feature and is not made
  worse by it, but the freshness guarantee above is a single-instance guarantee. A shared cache
  (Redis) would be the fix and would need a dependency decision.
- **`filterSkippedPosts` is not decremented by approve/reject.** `approvePost` deliberately keeps the
  flag (published + `FILTER_FAILED` records that a human overrode the filter, ADR-025), so approving
  a skipped post moves `pendingPosts` but leaves `filterSkippedPosts` unchanged. The eviction is
  correct; the metric's own definition is "skipped and not since unflagged or deleted".
- **`users.deactivated` has no producer.** Nothing in the codebase writes
  `UserStatus.deactivated`; it is read only to reject logins. The metric is permanently 0 and gets no
  eviction for that reason. If account self-deactivation is ever built, this spec's call-site list
  must gain it.

## Call-site index

The authoritative list, for auditing a new write path against this feature. Every row also appears
inline as a comment at the call site.

| Method | Cache |
|---|---|
| `AdminPostService.approvePost` | both |
| `AdminPostService.rejectPost` | both |
| `AdminService.moderateComment` | summary |
| `AdminService.suspendUser` | summary |
| `AdminService.restoreUser` | summary |
| `AdminRecipeService.deleteRecipe` | summary |
| `AdminRecipeService.approveRecipe` | summary |
| `AdminRecipeService.rejectRecipe` | summary |
| `PostService.createPost` | summary |
| `PostService.updatePost` | both |
| `PostService.attachMedia` | both |
| `PostService.deletePost` | both |
| `CommentService.deleteComment` | summary |
| `CommentService.reportComment` | summary |
| `RecipeService.createRecipe` | summary |
| `RecipeService.deleteRecipe` | summary |
| `PaymentWebhookService.handleNotification` (settled) | both |
| `PaymentWebhookService.handleNotification` (failed) | summary |
| `PlanClaimService.claimOrThrow` | summary |
| `AdminPlanService.updatePlan` | charts |
| `AdminPlanService.deactivatePlan` | charts |
| `ContentFilterOutboundAdapter.deliver` (settled verdict) | both |
| `ContentFilterOutboundAdapter.deliver` (filter unavailable) | summary |
| `ContentFilterOutboundAdapter.onDeliveryAbandoned` | summary |
| `PendingFilterSweepJob.sweepStalePendingPosts` (only when it settled something) | summary |

Deliberately absent: `VoteService.castVote`, `SavedItemService.save*/unsave*`,
`CommentService.createComment`, `PostService.attachRecipes` (writes only `post_recipe`, which no
dashboard metric reads), `RequestAllowanceService.record`, `AuthService.register`,
`AuthService.verifyEmail`, `AdminPlanService.createPlan`.
