# Feature Spec: Post Content Filtering

## Status

In progress

## Author / owner

Vegalife backend team

## Summary

Automatically screen every post a user asks to publish — static hard
rules first, then an embedding-based relevance score — and only let a
post become public when it passes. Posts that fail or land in an
ambiguous band are flagged for review instead of published.

## Problem / motivation

Issues [#33](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/33)
and [#34](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/34)
both list semantic filtering as an acceptance criterion, but PR #66
delivered only the create-posts API and the edit endpoint predates it —
neither issue's filtering job was ever implemented. Verified in the
codebase before this spec: no filtering logic in `PostService` /
`PostController`, no filter columns in any Flyway migration, no outbound
channel for content filtering, and no AI/embedding client.

Consequence today: any authenticated user's `publish: true` goes
straight to `published` and is immediately visible to guests and other
members (BR-POST-010). Nothing checks profanity, link spam, or whether
the content is about vegan food at all, and there is no record of what
was checked.

## Goals

- Screen content before it becomes public, on create and on every
  content edit, without slowing the API response down.
- Give each post an explicit filter state (`post.flag`) that callers can
  read, separate from the post lifecycle (`post.status`).
- Leave a durable audit row for every filter run so a reviewer can see
  what score and which rules produced a verdict.
- Recover automatically from a lost or exhausted queue message within
  24 hours rather than leaving a post stuck in `PENDING`.

## Non-goals

- Real-time filtering inside the HTTP request (explicitly a non-goal of
  issue #33) — the API returns as soon as the post is saved.
- Admin review tooling: no admin endpoints, queue UI, or "mark reviewed"
  API this sprint. `NEEDS_REVIEW` is only marked in the queue/audit data.
- Edit history / versioning (issue #34 non-goal).
- Filtering drafts that were never submitted for publication.
- Filtering any content type other than posts (comments, recipes, and
  messages are out of scope).
- Backfilling or re-screening pre-existing rows: `flag` starts as NULL
  everywhere and stays NULL until a post is actually filtered.
- Tracking the status a post held before it was flagged
  (`prev_status` does not exist).

## Requirements

### Functional Requirements

- [ ] FR-001: Creating a post with `publish: true` saves it with
      `status = created`, `flag = PENDING`, `publish_intent = true`, a
      `filter_queued_at` timestamp, and enqueues one `CONTENT_FILTER`
      outbound message in the same transaction.
- [ ] FR-002: Creating a post with `publish: false` (a draft) leaves
      `flag` NULL, sets no publish intent, and enqueues nothing; the
      draft is never filtered.
- [ ] FR-003: Editing the title, content, or media of a post whose
      status is `published` or `flagged` re-enqueues it for filtering
      with the same `PENDING` handshake.
- [ ] FR-004: Editing a draft, or withdrawing a post with
      `publish: false`, does not trigger filtering; withdrawal stays
      immediate and clears `publish_intent`.
- [ ] FR-005: The filter runs static rules first — required length /
      empty content, link-spam ratio, VN/EN profanity — and any
      violation rejects the post outright without calling the embedding
      service.
- [ ] FR-006: If static rules pass, the content is embedded and scored
      by cosine similarity against on-topic and off-topic centroids;
      the score maps to `PASSED` (≥ accept-threshold), `REJECTED`
      (< reject-threshold), or `NEEDS_REVIEW` (between the two).
- [ ] FR-007: Thresholds and the stale-pending age are configuration
      (`app.filter.accept-threshold` = 0.75, `reject-threshold` = 0.45,
      `sweep-max-age` = 24h), not constants in code.
- [ ] FR-008: `PASSED` sets `flag = PASSED` and publishes the post
      (`status = published`, `publishedAt` set) when publish intent was
      requested or the post is already published.
- [ ] FR-009: `REJECTED` and `NEEDS_REVIEW` set the flag accordingly and
      move the post to `status = flagged`, with a WARN log carrying the
      reasons (static) or the score band (semantic).
- [ ] FR-010: Every completed filter run writes exactly one row to
      `post_filter_log` (post, verdict, score, reasons, timestamp).
- [ ] FR-011: A post still `PENDING` after `app.filter.sweep-max-age`
      (24h) is moved to `NEEDS_REVIEW` / `flagged` with an ERROR log and
      an audit row, covering lost or exhausted queue messages.
- [ ] FR-012: The create and update responses, and the user's post list,
      expose `flag` (`null` = never filtered).

### Non-Functional Requirements

- [ ] NFR-SCALE-001: Filtering runs off the request thread through the
      existing PostgreSQL outbox (ADR-005) — no new broker, no new
      infrastructure beyond the HuggingFace HTTP API.
- [ ] NFR-SEC-001: The HuggingFace token comes only from the
      `HF_TOKEN` environment variable and is never logged or persisted.
- [ ] NFR-SEC-002: Filter reasons and scores are exposed to owners via
      the normal post responses; no internal prompt or model detail is
      returned.
- [ ] NFR-MAINT-001: The embedding provider is behind Spring AI's
      `EmbeddingModel` interface, so the provider can be swapped without
      touching the scorer or the service.
- [ ] NFR-MAINT-002: Every verdict path (pass, reject, review, sweep)
      writes the same audit shape to `post_filter_log`, so review tooling
      later reads one table.
- [ ] NFR-PERF-001: `POST /api/posts` and `PATCH /api/posts/{postId}`
      add no network calls — only one additional insert (outbox row)
      inside the existing transaction.

## Design overview

Two independent gates feed one verdict, produced by
`ContentFilterService` and applied by a `CONTENT_FILTER` adapter on the
existing outbound queue:

1. **Static rules** (`StaticRulesScorer`) — length/empty, link-spam
   ratio, and VN/EN profanity wordlists loaded from resource files. A
   violation is a hard reject; the embedding service is never called.
2. **Semantic relevance** (`EmbeddingRelevanceScorer`) — the text is
   embedded through an `EmbeddingModel` (custom HuggingFace
   feature-extraction client) and compared by cosine similarity against
   two precomputed centroids (on-topic vs off-topic, VN + EN seed
   corpora committed as resources). Thresholds come from `app.filter.*`.

State lives in three new columns on `post` (`flag`, `publish_intent`,
`filter_queued_at`) plus the `flagged` value added to `post.status`;
`flag` is deliberately separate from `status`: `status` stays the
lifecycle field, `flag` is the filter state, and NULL means "never
filtered". One new table, `post_filter_log`, holds the audit trail
(V18 moderation-log pattern). Architecture decisions and the dependency
choice are recorded in `docs/adrs/007-post-content-filtering.md`;
the queue reuse is ADR-005 with a new `CONTENT_FILTER` channel.

## Success metrics

- Both driving issues' filtering acceptance criteria are covered by
  automated tests that pass in `./mvnw clean verify` before the PR
  merges.
- 100% of posts created with publish intent end in a non-NULL `flag`
  within 24 hours (either a verdict or the sweep) — measured by the
  sweep job finding zero rows on a healthy system.
- No `published` post exists with `flag` NULL after this feature ships
  for new content (existing rows are exempt, never backfilled).

## Acceptance criteria

**As a** platform member, **I want to** publish posts that are screened
automatically, **so that** inappropriate or off-topic content does not
become publicly visible without review.

Issue #33 (create):

- [ ] Given an authenticated user creating a post with publish intent,
      when it is saved, then the response carries the post ID with
      `flag = PENDING` while the post itself stays `status = created`.
- [ ] Given a queued filter run, when static rules pass and the semantic
      score is in the accept band, then the post becomes `published`
      with `flag = PASSED`.
- [ ] Given a queued filter run with a static violation (profanity, link
      spam, empty/too short), when it runs, then the post becomes
      `flagged` with `flag = REJECTED` and the embedding service is not
      called.
- [ ] Given a semantic score between the two thresholds, when the run
      completes, then the post becomes `flagged` with
      `flag = NEEDS_REVIEW` and an audit row exists.
- [ ] Given a draft creation (`publish: false`), when it is saved, then
      `flag` is NULL and no `CONTENT_FILTER` message is enqueued.

Issue #34 (edit):

- [ ] Given a published post, when its title/content/media changes, then
      it is re-enqueued, and a passing re-run leaves it `published` with
      `flag = PASSED`.
- [ ] Given a published post, when a re-run fails the rules, then the
      post becomes `flagged` with `flag = REJECTED` or `NEEDS_REVIEW`.
- [ ] Given a flagged post, when a subsequent edit passes filtering, then
      it returns to `published`.
- [ ] Given a non-owner edit attempt, when it is submitted, then it is
      rejected exactly as before (ownership rules unchanged).
- [ ] Given create, update, and list responses, when returned, then each
      exposes `flag`.

## Risks / open questions

- **Provider availability**: the HuggingFace inference endpoint is an
  external dependency. Failures do not lose posts (the outbox retries),
  but a prolonged outage delays publishing; the 24h sweep converts the
  worst case into `NEEDS_REVIEW` rather than a permanent `PENDING`.
- **Threshold tuning**: 0.75 / 0.45 are initial values from the
  centroid spread, not measured against production traffic. Expect one
  tuning pass after real content flows through.
- **False rejects are user-visible**: a rejected post is `flagged`, not
  deleted, and re-editing re-runs the filter — but there is no admin
  release path until review endpoints exist (accepted for this sprint).
- **Wordlist coverage**: VN/EN profanity lists are static resources;
  they need periodic updates, which is a process, not a code, change.

---

## Related

- Driving issues: [#33](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/33),
  [#34](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/34)
- Business rules: `docs/brs/posts.md` (BR-FILTER-001…010, BR-POST-004)
- Architecture decision: `docs/adrs/007-post-content-filtering.md`
- Queue: `docs/adrs/005-persistent-outbound-message-queue.md`,
  `docs/feats/outbound-message-queue.md`
