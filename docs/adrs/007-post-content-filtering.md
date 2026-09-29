# ADR-007: Filter Post Content Before Publication

## Status

Accepted

## Date

2026-09-30

## Deciders

Project owner (behavior requested on issues #33 and #34), backend
implementation.

## Context

Posts are published as soon as the client asks for it: `POST /api/posts`
with `publish: true` returns a `published` post, and an edit with
`publish: true` republishes immediately (BR-POST-004 as written before
this decision). Nothing ever inspects what the post says. That is a
liability for a public community feed — link dumps, profanity, and
off-topic content would reach the public list endpoint
(`docs/apis/post/get-users-userid-posts.md`, BR-POST-010) with no gate
and no record of who/what let them through.

Issues #33 and #34 ask for a semantic content filter: screen a post
before it is public, score it for relevance to the vegan/vegetarian
community in Vietnamese and English, and re-screen it when its content
changes. The outcome must be visible to the caller (issue #33 AC) and
must not lose the post when the model is unavailable (issue #34 AC).

Constraints shaping the options:

- **No new infrastructure.** No message broker, no vector database, and
  no cloud AI service in the approved dependency matrix
  (`docs/arch/dependencies.md`).
- **The HTTP request must not wait on the model.** Embedding is an
  external network call with unbounded latency; create/edit must stay
  fast and must not fail because a provider is down.
- **The queue already exists.** ADR-005's PostgreSQL outbox
  (`outbound_message` + drainer + channel adapters) is in production for
  EMAIL, with retries, deferred delivery, and a 24-hour failure ceiling.
- **`post.status` already means something.** It is the lifecycle field
  BR-POST-010 turns into public visibility (`published` = public,
  everything else = owner/admin only). Filter state is a different axis.
- **Drafts must not be screened.** Users iterate; screening unpublished
  work costs embeddings and exposes unfinished text to automated
  judgement for no benefit.
- **Existing rows have no filter state.** Nothing may be backfilled, and
  "never filtered" must remain representable forever.
- **Bilingual, low-resource domain.** Vietnamese + English user content
  about food and lifestyle; no labelled training set exists and building
  one is out of scope.

## Decision

**Screen posts asynchronously, before publication, through the existing
ADR-005 outbox, using a HuggingFace embedding model behind Spring AI and
a three-band verdict stored in a dedicated `post.flag` column.**

Concretely:

1. **Queue channel.** A new `CONTENT_FILTER` channel on
   `outbound_message`. Enqueue happens in the caller's transaction when
   the post has publish intent (create with `publish: true`, edit with
   `publish: true`, or a content change to a post that is `published` or
   `flagged`); the same transaction sets `post.flag = PENDING` and
   `post.filter_queued_at = now`. There is no synchronous or real-time
   filtering path anywhere (BR-FILTER-006).
2. **Two-stage verdict.** `ContentFilterService` runs static rules
   first — minimum length, link-spam ratio, VN/EN profanity wordlists
   (BR-FILTER-001…003). A violation rejects the post outright and the
   embedding service is never called. Only content that passes is
   embedded and scored (BR-FILTER-004).
3. **Embedding client.** Spring AI 1.1.8 is added (BOM +
   `spring-ai-starter-model-openai`, property-managed) for the
   abstraction and for later chat work, with
   `spring.ai.openai.base-url=https://router.huggingface.co/v1` and
   `spring.ai.openai.api-key=${HF_TOKEN:}`. The embedding call actually
   used is a custom `HfEmbeddingModel implements EmbeddingModel`
   (`@Primary`, Spring `RestClient`) that POSTs
   `{"inputs":[...],"normalize":true}` to the HuggingFace
   feature-extraction endpoint and maps the `[[float]]` response 1:1 to
   `embed(List<String>)`. The custom client exists because the
   feature-extraction endpoint is batch-native and its shape is stable;
   the starter's OpenAI-compatible configuration is the fallback if the
   custom client is ever dropped, and is what a future chat feature
   would use.
4. **Three-band scoring.** Content is compared by cosine similarity
   with two precomputed centroids (on-topic and off-topic seed texts,
   VN + EN, committed as resources). The normalized margin between the
   two similarities is the relevance score; it maps to exactly one of
   `PASSED` (`score >= app.filter.accept-threshold`, default `0.75`),
   `REJECTED` (`score < app.filter.reject-threshold`, default `0.45`),
   or `NEEDS_REVIEW` (in between). Thresholds are configuration.
5. **Separate filter state.** `post.flag VARCHAR(16)` holds
   `PENDING` / `PASSED` / `REJECTED` / `NEEDS_REVIEW`, or NULL for
   "never filtered". There is deliberately no `NOT_FILTERED` value and
   no `prev_status` column. `post.status` gains one new value,
   `flagged`, used when a verdict is not `PASSED`; publishing happens
   only when a run returns `PASSED` (BR-FILTER-007, BR-FILTER-008).
6. **Publish intent.** A boolean `post.publish_intent` records that the
   user asked for publication, so a post created as a draft stays
   unfiltered forever and a withdrawn post stops being filtered
   (BR-FILTER-005).
7. **Audit.** Every completed run writes one `post_filter_log` row
   (post id, verdict, score, reasons, timestamp) in a table created in
   the same migration, following the V18 moderation-log pattern
   (BR-FILTER-010).
8. **Sweep.** A scheduled job moves any post still `PENDING` after
   `app.filter.sweep-max-age` (default 24h) to `NEEDS_REVIEW` /
   `flagged` with an ERROR log and an audit row, bounding the damage of
   a lost or exhausted queue message (BR-FILTER-009).

## Considered options

- **Option A — filter synchronously inside the create/edit request** —
  simplest possible wiring, but every publish becomes as slow as the
  embedding call and every provider outage becomes a 500 for the user.
  Rejected.
- **Option B — a new broker (RabbitMQ / Redis Streams) for filter
  jobs** — good push semantics, but adds infrastructure that is not in
  the approved dependency matrix, for one low-volume channel. Rejected;
  ADR-005 already solved durable async work in this codebase.
- **Option C — reuse the ADR-005 outbox with a new `CONTENT_FILTER`
  channel** — durable in the same transaction as the post write, retries
  and deferred delivery already built and tested, no new infrastructure,
  and the queue table was explicitly designed to be generic over
  channels. **(Chosen.)**
- **Option D — a managed moderation API (cloud text-moderation
  endpoints)** — would move the problem to a vendor, but is outside the
  approved matrix, adds cost and data export, and gives no control over
  Vietnamese relevance, which is the actual requirement. Rejected.
- **Option E — a locally hosted model (DJL / ONNX embeddings)** — no
  external dependency and no token to manage, but adds a heavyweight
  runtime dependency plus a model artifact to the build, none of which
  is approved. Rejected for now.
- **Option F — store filter state in `post.status` (reuse an existing
  value such as `processed`)** — no new column, but conflates two
  independent axes: a post could not be simultaneously "published"
  (visible) and "under review", and NULL would become unrepresentable,
  so every pre-existing row would have to be backfilled with a lie.
  Rejected; `flag` is the fourth state axis (lifecycle, visibility,
  moderation, filtering) and is stored separately (decision 5).
- **Option G — filter every post, drafts included** — catches content
  earlier, but burns embeddings on work in progress and contradicts the
  requirement that drafts are never screened. Rejected; publish intent
  decides (decision 6).
- **Option H — track `prev_status` so a passing verdict can restore the
  exact prior status** — would make verdict application fully symmetric,
  but no restore path exists this sprint (a passing edit returns a
  flagged post to `published`), and the column invites status-machine
  bugs. Rejected (decision 5).

## Consequences

**Positive:**

- Create/edit stay fast and availability-independent: they commit a post
  row plus an outbox row, nothing else (issue #34 AC on resilience).
- Reuse means retries, deferred delivery, retention, and observability
  come from code that already has integration coverage for EMAIL.
- Publication is provably gated: a post cannot become `published`
  without a `PASSED` verdict, and every verdict has an audit row.
- Thresholds, the sweep age, and the embedding endpoint/model are all
  configuration, so tuning does not require logic changes.
- The scorer is static-rules-first, so obvious spam never reaches the
  network and the provider bill tracks real ambiguity.

**Negative / trade-offs:**

- **Publication is now asynchronous.** A post created with
  `publish: true` appears publicly only after the drainer polls (up to
  ~5s) plus the embedding call, instead of being live before the
  response returns. The caller sees `created` + `flag: PENDING`.
- **At-least-once delivery can re-run a filter.** A crash after a
  successful verdict but before the `COMPLETED` commit re-runs the
  filter; the verdict is deterministic given the same inputs, so the
  post ends in the same place, but `post_filter_log` gains a second
  row. Accepted — identical to the email-queue trade-off in ADR-005.
- **Quality is bounded by the seed corpus.** Centroids are computed
  from committed seed texts; a skewed corpus produces systematic
  misclassification that no threshold change fully fixes. Mitigated by
  the committed regeneration path and by `NEEDS_REVIEW` keeping
  uncertain content out of public view rather than deleting it.
- **`NEEDS_REVIEW` has no operator surface this sprint.** No admin
  endpoints exist to re-run or override a verdict, so affected posts sit
  in `flagged` until their owner edits them or a later sprint adds
  review tooling. Explicitly out of scope for issues #33/#34.
- **New external credential.** `HF_TOKEN` must exist in production.
  Spring AI's OpenAI autoconfiguration refuses to start with an empty
  `spring.ai.openai.api-key`, so production without `HF_TOKEN` now
  fails fast at startup instead of booting and failing every run; that
  is deliberate (a filter that can never run should not come up), but it
  turns a missing credential into a deployment blocker. Test, dev, and
  integration profiles therefore override the key with a committed dummy
  value (`${HF_TOKEN:<profile>-dummy-key-not-a-secret}`) so the context
  loads with no credential present — the dummies are never a secret and
  never grant anything.
- **Profanity wordlists become a maintained asset.** VN/EN lists are
  committed resources; misses are a product risk that no scoring band
  compensates for.
- **Two more things to migrate and index** (`post` columns,
  `post_filter_log`), plus the drainer's existing 5s polling now also
  covers this channel.

## Verification

- Issues #33 and #34 acceptance criteria are the primary check: publish
  intent returns the post id with `flag: PENDING`, a passed run
  publishes it, a static violation rejects without an embedding call, a
  mid-band score flags it with an audit row, drafts are never queued,
  and a content edit of a published post re-enqueues.
- Revisit if any of these happen: the `NEEDS_REVIEW` share of runs
  exceeds ~10% over a week (corpus or thresholds are wrong); queue
  depth for `CONTENT_FILTER` grows persistently (provider latency or
  availability); the static rules start rejecting content that
  reviewers consider legitimate (wordlist too aggressive); or the
  project adopts a chat/multi-modal AI feature, at which point the
  relationship between the custom `HfEmbeddingModel` and the Spring AI
  starter configuration should be simplified to one path.
