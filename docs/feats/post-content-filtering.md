# Feature Spec: Post Content Filtering

## Status

In progress

## Author / owner

Vegalife backend team

## Summary

Automatically screen every post a user asks to publish with an
embedding-based relevance score, and only let a post become public when
it passes. Posts that fail or land in an ambiguous band are left
`unpublished` (with a `REJECTED`/`NEEDS_REVIEW` flag) for review instead
of published.

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
members (BR-POST-010). Nothing checks whether the content is about
vegan food at all, or what it contains.

## Goals

- Screen content before it becomes public, on create and on every
  content edit, without slowing the API response down.
- Give each post an explicit filter state (`post.flag`) that callers can
  read, separate from the post lifecycle (`post.status`).
- Recover automatically from a lost or exhausted queue message within
  24 hours rather than leaving a post stuck in `PENDING`.

## Non-goals

- Real-time filtering inside the HTTP request (explicitly a non-goal of
  issue #33) — the API returns as soon as the post is saved.
- Admin review tooling: no admin endpoints, queue UI, or "mark reviewed"
  API this sprint. `NEEDS_REVIEW` is only marked in `post.flag` and the
  logs.
- Edit history / versioning (issue #34 non-goal).
- Filtering drafts that were never submitted for publication.
- Filtering any content type other than posts (comments, recipes, and
  messages are out of scope).
- Backfilling or re-screening pre-existing rows: `flag` starts as NULL
  everywhere and stays NULL until a post is actually filtered.
- Tracking the status a post held before it was unpublished by the filter
  (`prev_status` does not exist).

## Requirements

### Functional Requirements

- [ ] FR-001: Creating a post with `publish: true` saves it with
      `status = created`, `flag = PENDING`, and enqueues one
      `CONTENT_FILTER` outbound message in the same transaction.
- [ ] FR-002: Creating a post with `publish: false` (a draft) leaves
      `flag` NULL and enqueues nothing; the draft is never filtered.
- [ ] FR-003: Editing the title, content, or media of a post whose
      status is `published` or `unpublished` (a post the filter did not
      pass) re-enqueues it for filtering with the same `PENDING`
      handshake.
- [ ] FR-004: Editing a draft, or withdrawing a post with
      `publish: false`, does not trigger filtering; withdrawal stays
      immediate (a withdrawn `unpublished` post returns to `created` but
      keeps its `flag`).
- [ ] FR-005: The filter runs no static-rule stage — length, links, and
      wordlists are never evaluated as separate rules — so every
      queued run embeds the content.
- [ ] FR-006: The content is embedded and scored
      by cosine similarity against on-topic and off-topic centroids,
      with the text embedding and both centroids mean-centered over the
      seed corpus first; the score maps to `PASSED` (≥ accept-threshold),
      `REJECTED` (< reject-threshold), or `NEEDS_REVIEW` (between the
      two).
- [ ] FR-007: Thresholds and the stale-pending age are configuration
      (`app.filter.accept-threshold` = 0.65, `reject-threshold` = 0.43,
      `sweep-max-age` = 24h), not constants in code.
- [ ] FR-008: `PASSED` sets `flag = PASSED` and publishes the post
      (`status = published`, `publishedAt` set) — every queued run was
      enqueued by an explicit publish request or a content change on a
      `published`/`unpublished` post.
- [ ] FR-009: `REJECTED` and `NEEDS_REVIEW` set the flag accordingly and
      move the post to `status = unpublished`, with a WARN log carrying
      the score band.
- [ ] FR-011: A `CONTENT_FILTER` recipient whose newest outbound message
      is older than `app.filter.sweep-max-age` (24h) and whose post is
      still `PENDING` is moved to `NEEDS_REVIEW` / `unpublished` with an
      ERROR log, covering lost or exhausted queue messages.
- [ ] FR-012: The create and update responses, and the user's post list,
      expose `flag` (`null` = never filtered).

### Non-Functional Requirements

- [ ] NFR-SCALE-001: Filtering runs off the request thread through the
      existing PostgreSQL outbox (ADR-005) — no new broker, no new
      infrastructure beyond the HuggingFace HTTP API.
- [ ] NFR-SEC-001: The HuggingFace token comes only from the
      `HF_TOKEN` environment variable and is never logged or persisted.
- [ ] NFR-SEC-002: Only `post.flag` is exposed to owners via the normal
      post responses; scores, reasons, and model details stay internal
      (logs only).
- [ ] NFR-MAINT-001: The embedding provider is behind Spring AI's
      `EmbeddingModel` interface, so the provider can be swapped without
      touching the scorer or the service.
- [ ] NFR-PERF-001: `POST /api/posts` and `PATCH /api/posts/{postId}`
      add no network calls — only one additional insert (outbox row)
      inside the existing transaction.

## Design overview

One gate produces the verdict, produced by
`ContentFilterService` and applied by a `CONTENT_FILTER` adapter on the
existing outbound queue:

1. **Semantic relevance** (`EmbeddingRelevanceScorer`) — the text is
   embedded through an `EmbeddingModel` (custom HuggingFace
   feature-extraction client) and compared by cosine similarity against
   two precomputed centroids (on-topic vs off-topic, VN + EN seed
   corpora committed as resources), mean-centered over the seed corpus
   before comparison. Thresholds come from `app.filter.*`.

State lives in one new column on `post` (`flag`);
`flag` is deliberately separate from `status`: `status` stays the
lifecycle/visibility field, `flag` is the filter state, and NULL means
"never filtered". A non-`PASSED` verdict sets `status = unpublished`
(the short-lived `flagged` status value from ADR-007 was retired by
ADR-011, migration `V31`). Enqueue age is read from the outbox row's `created_at`
(written in the same transaction as `flag = PENDING`), not from a post
column. The verdict persists only in `post.flag`; the score and
reasons are written to SLF4J WARN/ERROR logs — there is no audit
table. Architecture decisions and the dependency
choice are recorded in `docs/adrs/007-post-content-filtering.md`;
the queue reuse is ADR-005 with a new `CONTENT_FILTER` channel.

## Success metrics

- Both driving issues' filtering acceptance criteria are covered by
  automated tests that pass in `./mvnw clean verify` before the PR
  merges.
- 100% of posts submitted for publication end in a non-NULL `flag`
  within 24 hours (either a verdict or the sweep) — measured by the
  sweep job finding zero rows on a healthy system.
- No `published` post exists with `flag` NULL after this feature ships
  for new content (existing rows are exempt, never backfilled).

## Acceptance criteria

**As a** platform member, **I want to** publish posts that are screened
automatically, **so that** inappropriate or off-topic content does not
become publicly visible without review.

Issue #33 (create):

- [ ] Given an authenticated user creating a post with `publish: true`,
      when it is saved, then the response carries the post ID with
      `flag = PENDING` while the post itself stays `status = created`.
- [ ] Given a queued filter run, when the semantic score is in the accept
      band, then the post becomes `published` with `flag = PASSED`.
- [ ] Given a queued filter run with an off-topic score below the reject
      threshold, when it runs, then the post becomes `unpublished` with
      `flag = REJECTED` after exactly one embedding call.
- [ ] Given a semantic score between the two thresholds, when the run
      completes, then the post becomes `unpublished` with
      `flag = NEEDS_REVIEW`.
- [ ] Given a draft creation (`publish: false`), when it is saved, then
      `flag` is NULL and no `CONTENT_FILTER` message is enqueued.

Issue #34 (edit):

- [ ] Given a published post, when its title/content/media changes, then
      it is re-enqueued, and a passing re-run leaves it `published` with
      `flag = PASSED`.
- [ ] Given a published post, when a re-run scores below the thresholds,
      then the post becomes `unpublished` with `flag = REJECTED` or
      `NEEDS_REVIEW`.
- [ ] Given an `unpublished` post, when a subsequent edit passes
      filtering, then it returns to `published`.
- [ ] Given a non-owner edit attempt, when it is submitted, then it is
      rejected exactly as before (ownership rules unchanged).
- [ ] Given create, update, and list responses, when returned, then each
      exposes `flag`.

## Risks / open questions

- **Provider availability**: the HuggingFace inference endpoint is an
  external dependency. Failures do not lose posts (the outbox retries),
  but a prolonged outage delays publishing; the 24h sweep converts the
  worst case into `NEEDS_REVIEW` rather than a permanent `PENDING`.
- **Threshold tuning**: 0.65 / 0.43 are derived from the measured
  centered seed distribution (highest off-topic 0.4058, lowest
  on-topic 0.7279), not measured against production traffic. Expect
  one tuning pass after real content flows through.
- **False rejects are user-visible**: a rejected post is `unpublished`
  (with `flag = REJECTED`/`NEEDS_REVIEW`), not deleted, and re-editing
  re-runs the filter — but there is no admin release path until review
  endpoints exist (accepted for this sprint).

---

## Related

- Driving issues: [#33](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/33),
  [#34](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/34)
- Business rules: `docs/brs/posts.md` (BR-FILTER-004…009, BR-POST-004)
- Architecture decision: `docs/adrs/007-post-content-filtering.md`
- Queue: `docs/adrs/005-persistent-outbound-message-queue.md`,
  `docs/feats/outbound-message-queue.md`
