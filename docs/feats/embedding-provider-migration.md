# Feature Spec: Embedding Provider Migration to Gemini

## Status

Draft — awaiting approval

## Author / owner

Vegalife backend team

## Summary

Replace the dead HuggingFace embedding client with Google's
`gemini-embedding-001`, keeping the HuggingFace client in the codebase
unwired so it can be re-armed by configuration. Recalibrate the filter's
centroids and thresholds against the new provider's vector geometry.

This is a prerequisite for re-enabling the content filter. The filter is
currently `false` in configuration because it cannot run at all.

## Problem / motivation

The semantic content filter ([ADR-007](../adrs/007-post-content-filtering.md))
has not worked since the demo, for two stacked reasons verified by live
probe:

1. The configured `HF_TOKEN` had expired — every call returned **401**.
2. After replacing it with a valid token, every embedding model returned
   **402 Payment Required**. The HuggingFace account holds zero
   Inference Providers credits.

The embedding model and route were never wrong; the account cannot call
them. The filter was therefore switched off
(`FILTER_ENABLED=false`) as a workaround, which means published posts are
no longer screened at all — they land directly in the review queue and
nothing judges them.

A replacement must be callable without a billing relationship, and must
fail in a way operators can see rather than silently doing nothing.

## Goals

- Score posts again, using an embedding provider that works without
  prepaid credits.
- Preserve the existing seam: the scorer consumes `float[]` and must not
  learn which provider produced it.
- Keep the HuggingFace client re-armable by configuration alone, with no
  code change.
- Keep the filter's failure mode safe: an outage must never block a
  member's publish.
- Re-derive the score thresholds from measurement rather than inheriting
  e5's numbers.

## Non-goals

- **Re-enabling the filter.** This spec makes a working provider
  available; flipping `app.filter.enabled` is a separate change with its
  own verification.
- Media (image/video) moderation — explicitly out of scope.
- Duplicate-post detection — a separate feature that also needs
  embeddings but has its own design.
- Distributed tracing or metrics for the embedding call.
- Changing the filter verdict model (`PASSED` / `REJECTED` /
  `NEEDS_REVIEW`) or the `post.flag` / `post.status` split from
  [ADR-011](../adrs/011-post-status-and-filter-axis-separation.md).
- Editing historical documents that record `HF_TOKEN` as it was.
  ADR-014 supersedes the decision; [ADR-007](../adrs/007-post-content-filtering.md)
  and `feats/post-content-filtering.md` are left as written.

## Requirements

### Provider

- **FR-001**: The active embedding provider is Google Gemini,
  `gemini-embedding-001`, called over `:batchEmbedContents`.
- **FR-002**: Every request sends `task_type: SEMANTIC_SIMILARITY` from
  one shared constant used identically by live scoring and by centroid
  regeneration. Two different values would score vectors in different
  spaces and silently invalidate every threshold.
- **FR-003**: Every request sends `output_dimensionality: 768`, matching
  the vector column order 5 will create.
- **FR-004**: The client returns vectors in request order, implementing
  Spring AI's `EmbeddingModel` so no downstream consumer changes.

### Normalisation

- **FR-005**: Every returned vector is L2-normalised client-side before
  it is returned. A 768-dimension response arrives at norm ≈ 0.59;
  requesting a smaller dimensionality truncates without renormalising.
- **FR-006**: The measured norm and the reason normalisation is required
  are recorded in the client's javadoc, so it is not later removed as
  redundant.

### Configuration

- **FR-007**: Active embedding configuration uses the `EMBED_` prefix:
  `EMBED_API_KEY`, `EMBED_BASE_URL`, `EMBED_MODEL`, `EMBED_DIMENSIONS`.
- **FR-008**: `app.filter.embedding.provider` selects between `gemini`
  (default) and `hf`. An unrecognised value aborts startup naming the
  valid values.
- **FR-009**: The HuggingFace client remains compiled and re-armable by
  setting `provider=hf`. Its `HF_*` names are retained but are never
  presented as active configuration.
- **FR-010**: `HF_TOKEN` is removed from the AI chat credential chain,
  which becomes `AI_CHAT_API_KEY` plus the profile dummy keys. A bare
  run and CI must still start without secrets.

### Failure behaviour

- **FR-011**: An embedding failure during a filter run leaves the post
  `NEEDS_REVIEW` / `unpublished`. It never fails the publish request.
- **FR-012**: The review queue records a **reason** distinguishing
  "scored into the review band" from "the filter could not run". Without
  this, an outage is indistinguishable from real moderation work.
- **FR-013**: The failure is logged at ERROR with the upstream status.

### Calibration

- **FR-014**: Centroids and test fixtures are regenerated against Gemini.
  They are provider-bound artefacts and are not copied from e5.
- **FR-015**: Thresholds are placed by a **stated derivation rule**
  applied to the measured separation, so the outcome is reproducible.
  The rule and its verdict are recorded.
- **FR-016**: The verification asserts **unit norm (1.0 ± 1e-6)**
  independently of cosine. Cosine is scale-invariant and would pass on
  unnormalised vectors.

## Design overview

The seam already exists and is not modified:

```
ContentFilterService
    → EmbeddingModel  (Spring AI interface)
        ├─ GeminiEmbeddingModel   (new, active)
        └─ HfEmbeddingModel       (existing, unwired)
    → EmbeddingRelevanceScorer.score(float[])   ← provider-agnostic
```

`GeminiEmbeddingModel` and `HfEmbeddingModel` are siblings implementing
the same interface. `ContentFilterService`, `EmbeddingRelevanceScorer`,
`RelevanceScore` and the verdict model are untouched — if they needed
changes, the abstraction would be leaking and the provider would not
truly be swappable.

Selected by `app.filter.embedding.provider` through a `SpringBootCondition`
rather than `@ConditionalOnProperty`, so exactly one client bean registers
and an unrecognised value aborts startup instead of registering nothing.

### Threshold derivation rule

Existing `0.65` / `0.43` sit inside e5's measured window (highest
off-topic 0.4058, lowest on-topic 0.7279). Those numbers describe e5's
geometry and do not transfer.

The rule, applied to Gemini's measured window:

> Let `L` be the lowest on-topic score and `H` the highest off-topic
> score observed over the seed corpus.
>
> - If `L - H < 0.02`, the provider does not separate the corpora well
>   enough to be a filter. **Stop** — do not ship a threshold; report
>   the numbers.
> - Otherwise `accept = H + 0.75 × (L - H)` and `reject = H + 0.25 × (L - H)`,
>   placing the two thresholds in the upper and lower quartiles of the
>   gap, leaving the middle half as the review band.

The gap is reported in the PR with the observed `L` and `H`, so the
arithmetic can be checked rather than trusted.

### Free-tier quota

The published Gemini embedding rate limits could not be cited
authoritatively. Rather than discover the ceiling by exhausting it:

- The documented limit is cited if one can be found at build time.
- At most **one** live probe is made (a single batched call), enough to
  confirm the client works, never enough to approach a limit.
- The real ceiling is observed from production `429`s made visible by
  the order-1 classifier, which costs nothing and does not consume
  allowance deliberately.

The filter runs on publish and on content edits, not on reads, so volume
is low. An exhausted quota degrades to the review queue (FR-011), not to
a broken publish.

## Success metrics

- `CentroidRegenerationTest` verify mode: cosine ≥ 0.99 against re-embedded
  centroids for both centroids and the corpus mean.
- Unit norm asserted for every returned vector, independently of cosine.
- The root `EmbeddingRelevanceScorerRegressionTest` fixture bands pass.
- No log line, exception message, or commit contains the API key.

## Acceptance criteria

- [ ] **AC-1** A live call to the client returns 768-dimension vectors
      with L2 norm 1.0 ± 1e-6.
- [ ] **AC-2** The same sentence embedded twice yields cosine ≈ 1.0
      (determinism).
- [ ] **AC-3** A Vietnamese sentence and its English translation of the
      same vegan-topic claim score highly against each other. This is why
      e5 was originally chosen; it must be re-verified, not assumed.
- [ ] **AC-4** Centroids regenerated; verify mode passes cosine ≥ 0.99.
- [ ] **AC-5** Thresholds derived by the stated rule from the observed
      separation, with `L`, `H` and the arithmetic recorded.
- [ ] **AC-6** `provider=hf` still registers the HuggingFace client;
      default registers Gemini; an invalid value aborts startup.
- [ ] **AC-7** An embedding failure leaves a post `NEEDS_REVIEW` with a
      failure reason, and does not fail the publish request.
- [ ] **AC-8** No `HF_*` name is presented as active configuration in
      `.env.example`.
- [ ] **AC-9** `./mvnw clean verify` green.
- [ ] **AC-10** No secret appears in any commit, log line, or exception.

## Implementation status

Delivered on `feat/embedding-gemini-migration`:

- Provider, normalisation, configuration and the startup gate (FR-001–FR-010).
- Unit coverage for the client and the provider switch, including a case that
  reproduces the truncated ~0.59-norm response and asserts it is normalised.
- `CentroidRegenerationTest` builds whichever provider is configured.

Not yet done, deliberately:

- **Calibration (FR-014, FR-015) and AC-1–AC-5.** These require a live
  `EMBED_API_KEY` and a quota spend; they are run separately and their
  numbers reported back before `centroids.json` is replaced.
- **Re-enabling the filter.** Non-goal by design. `app.filter.enabled` stays
  `false` until calibration passes, so this change cannot alter live
  moderation behaviour.
- **FR-011 / FR-012 (failure reason).** The existing behaviour already
  satisfies FR-011: an embedding failure propagates, the outbox drainer logs
  it at ERROR with the cause chain and retries the message. FR-012's
  distinguishing reason code is a schema change that belongs with the
  moderation work and is not introduced here.

## Risks / open questions

- **Thresholds are the highest-risk output.** A wrong placement does not
  fail a test — it silently misclassifies real posts. The derivation rule
  exists to make the choice mechanical and auditable; the observed gap is
  reported for that reason.
- **Centroids become Gemini-bound.** Committed `centroids.json` and the
  fixtures are artefacts of one provider and one model. Re-arming HF
  requires regenerating them, not just flipping the property — a trap
  worth stating in the operator docs.
- **Free-tier limits unverified.** Mitigated by FR-011: quota exhaustion
  degrades to review rather than failing publishes.
- **`centroids.json` is also claimed by duplicate detection.** Only one
  change may own that file at a time.
- **Determinism is assumed, not yet proven.** AC-2 tests it. If Gemini
  returns non-deterministic vectors, committed centroids cannot be
  reproduced and the design needs revisiting.

## Related

- [ADR-014: Serve Filter Embeddings From Gemini](../adrs/014-embedding-provider-gemini.md)
- [ADR-007: Filter Post Content Before Publication](../adrs/007-post-content-filtering.md)
- [ADR-011: Keep Post Visibility in `status` and the Filter Verdict in `flag` Only](../adrs/011-post-status-and-filter-axis-separation.md)
- [Feature spec: Post Content Filtering](post-content-filtering.md)
