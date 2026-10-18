# ADR-014: Serve Filter Embeddings From Gemini, Retaining the HuggingFace Client Unwired

## Status

Accepted — supersedes decision 3 of
[ADR-007](007-post-content-filtering.md) (the embedding client only).
Decisions 2, 4, 5, 6, 7 and 8 of ADR-007 stand unchanged.

## Date

2026-10-18

## Deciders

Project owner (direction set in the post-demo triage), backend
implementation.

## Context

ADR-007 decision 3 wired the content filter to HuggingFace's
feature-extraction route through a custom `HfEmbeddingModel`. That
decision assumed the HuggingFace account had Inference Provider credits.
It does not, and the failure was two faults stacked on top of each other:

1. The configured `HF_TOKEN` had expired. A live call returned **401**.
2. After replacement with a valid token, every embedding model returned
   **402 Payment Required** — the account has zero Inference Providers
   credit.

So the model and the route were never wrong. The account cannot call
them. The filter has therefore been inert since the demo, and
`app.filter.enabled` was left `false` as a workaround rather than fixed.

Two constraints bound any replacement:

- **The filter must work again**, not merely be re-pointed at another
  provider that might also fail quietly.
- **The HuggingFace client must survive as re-armable code**, not be
  deleted — the owner's instruction was to keep the implementation and
  leave it unwired, so that a future credit balance could restore it by
  configuration alone.

## Decision

1. **Gemini's `gemini-embedding-001` becomes the active embedding
   provider.** It is callable on the free tier with no billing
   relationship, and was verified live this session: HTTP 200, 768
   dimensions returned, and `:batchEmbedContents` accepting 4 inputs in
   one request. `gemini-embedding-2` was rejected: it is multimodal and
   exposes no `task_type`, discarding a knob the filter depends on.

2. **`task_type: SEMANTIC_SIMILARITY` is sent on every call and is a
   single shared constant.** Asymmetric task types (`RETRIEVAL_DOCUMENT`
   vs `RETRIEVAL_QUERY`) would apply different transformations to the
   post and to the centroids. Centroid regeneration and live scoring
   must therefore use the identical constant, or the space the
   thresholds were calibrated in no longer exists.

3. **The client L2-normalises every returned vector before it is used.**
   This is load-bearing, not defensive. Measured this session: a
   768-dimension call returns vectors with **norm 0.588–0.592**, while
   the default 3072-dimension call returns **1.000000**. Asking for
   `output_dimensionality: 768` truncates and does **not** renormalise.
   ADR-007 decision 4's mean-centering arithmetic was derived against
   unit-length e5 vectors, so unnormalised input would shift every score
   silently, with no error and no failed test.

   Because cosine similarity is scale-invariant, a cosine-only
   regression check **cannot** detect a missing normalisation: it would
   pass on 0.59-norm vectors. The verification therefore asserts unit
   norm as a check independent of cosine.

4. **The provider is selected by configuration, not compiled in.**
   `app.filter.embedding.provider` chooses between `gemini` (default)
   and `hf`. An unrecognised value aborts startup rather than booting
   with no usable client.

5. **The HuggingFace client is retained, unwired.** `HfEmbeddingModel`,
   `HfEmbeddingProperties` and `HfEmbeddingConfiguration` survive; only
   their bean-registration condition changes. Re-arming them requires no
   code change.

6. **Active embedding configuration uses the `EMBED_` variable prefix**
   (`EMBED_API_KEY`, `EMBED_BASE_URL`, `EMBED_MODEL`,
   `EMBED_DIMENSIONS`). The dormant HuggingFace block keeps its historical
   `HF_*` names, which now describe a configuration a deployer is never
   told to set.

7. **`HF_TOKEN` is removed from the AI chat credential chain.** ADR-007
   introduced the chain `AI_CHAT_API_KEY -> HF_TOKEN` because both were
   the same account's key. Now that HuggingFace is not an embedding
   credential, the chain is vestigial coupling that would send an
   embedding secret to a chat provider. It becomes `AI_CHAT_API_KEY`
   alone, with the profile dummy keys kept so a bare run and CI still
   start without secrets.

8. **Thresholds are re-derived, not carried over.** ADR-007 decision 4's
   `0.65` / `0.43` sit inside an e5-specific measured window (highest
   off-topic 0.4058, lowest on-topic 0.7279). Those numbers describe e5's
   geometry. The Gemini window must be measured and the thresholds
   placed from that measurement, using a stated derivation rule so the
   outcome is reproducible and does not depend on whoever is looking at
   the numbers.

9. **An embedding failure queues the post for review; it never fails the
   publish.** Same shape as a disabled filter: `flag = NEEDS_REVIEW`,
   `status = unpublished`. The review queue must record *why* a post is
   there, so an operator can distinguish "the filter judged this" from
   "the filter could not run" — without that, an outage manufactures a
   queue indistinguishable from real moderation work.

## Alternatives considered

**Keep HuggingFace and add credits.** Rejected as the primary path: it
makes the filter depend on a prepaid balance that has already lapsed
once, and leaves the system inert whenever it does. The client is kept
so this remains possible later.

**OpenAI- or OpenRouter-hosted embeddings for symmetry with chat.**
OpenRouter does not serve embeddings. A second paid provider for an
internal filter is not justified.

**Local model (e.g. a bundled ONNX encoder).** Removes the network
dependency entirely, but adds a large binary to the build and shifts the
workload onto the application host. Out of scope; worth revisiting only
if provider outages prove frequent.

**Snapshot post embeddings instead of recalibrating.** Rejected: it
addresses duplication detection (a different feature), not the question
of which vectors the filter scores against.

## Consequences

- The filter can be re-enabled (a separate change) once the Gemini
  calibration is verified.
- Committed `centroids.json` and the test fixtures become Gemini-bound.
  They are artefacts of one provider and one model; switching provider
  means regenerating, not copying.
- Two clients exist in the codebase, one of them dormant. The dormant
  one must not be mistaken for live configuration — `.env.example`
  presents no active `HF_*` line.
- The free tier's rate limits are not documented in a form that can be
  cited precisely. This is accepted: the filter runs on publish and on
  content edits, not on reads, and an exhausted quota degrades to the
  review queue (decision 9) rather than to a broken publish.

## References

- [ADR-007: Filter Post Content Before Publication](007-post-content-filtering.md)
- [ADR-011: Keep Post Visibility in `status` and the Filter Verdict in `flag` Only](011-post-status-and-filter-axis-separation.md)
- [Feature spec: Embedding Provider Migration](../feats/embedding-provider-migration.md)
