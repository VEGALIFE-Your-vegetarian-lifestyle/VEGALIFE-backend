# Filter Calibration: `gemini-embedding-001`

## Status

Accepted — thresholds in effect, filter enabled by default.

## Summary

Records the measured separation between the on-topic and off-topic seed
corpora under `gemini-embedding-001` at 768 dimensions, the thresholds
the derivation rule produces from it, the recommendation, and the values
the owner selected.

This document exists because the numbers are **provider-bound**. They
describe one model's vector geometry and do not transfer to HuggingFace,
to a different Gemini model, or to a different output dimensionality.
When the embedding model changes, this document is replaced, not amended.

## Context

The content filter scores a post by comparing its embedding against two
centroids — one built from on-topic seed texts, one from off-topic — after
mean-centering over the combined corpus
([ADR-007](../adrs/007-post-content-filtering.md)). The score is:

```
score = (cos(text − mean, onTopic − mean) − cos(text − mean, offTopic − mean) + 1) / 2
```

clamped to `[0, 1]`. The band comes from `app.filter.accept-threshold` and
`app.filter.reject-threshold`.

The previous thresholds (`0.65` / `0.43`) were derived from **e5**
(`intfloat/multilingual-e5-large-instruct`, 1024 dimensions), whose
measured window was highest-off-topic `0.4058` and lowest-on-topic
`0.7279`. When the provider moved to Gemini
([ADR-014](../adrs/014-embedding-provider-gemini.md)), both the centroids
and the thresholds became invalid: the centroids were a different model's
vectors, and the thresholds described a geometry that no longer existed.

## Method

Corpora: `src/main/resources/filter/seed-on-topic.txt` (**19** seeds) and
`seed-off-topic.txt` (**20** seeds), mixed Vietnamese and English.

Procedure, run via `CentroidRegenerationTest` with `CENTROIDS_WRITE=true`:

1. Embed every seed with `gemini-embedding-001`, `task_type:
   SEMANTIC_SIMILARITY`, `output_dimensionality: 768`.
2. L2-normalise each returned vector (the API returns them at norm ≈ 0.59).
3. Compute the on-topic centroid, off-topic centroid, and the combined
   corpus mean.
4. Mean-center, then score **every seed** against the centroids.
5. Take `L` = the lowest on-topic seed score, `H` = the highest off-topic
   seed score.

## Measured result

```
L  (lowest on-topic score)   = 0.7553439568530472
H  (highest off-topic score) = 0.3490760399229218
L − H  (separation)          = 0.4062679169301254
```

The separation is wide. The rule's floor is `0.02`; this is more than
twenty times that, so the corpora are clearly distinguishable under this
model and no "stop, this provider cannot filter" condition applies.

### Derivation rule applied

```
accept = H + 0.75 × (L − H) = 0.349076 + 0.304701 = 0.653777
reject = H + 0.25 × (L − H) = 0.349076 + 0.101567 = 0.450643
```

## Recommendation

**`accept = 0.6538`, `reject = 0.4506`** — the rule's literal output.

Rationale: the rule exists so the choice is mechanical and auditable
rather than tuned by hand. Rounding to `0.65` / `0.45` sits within
`0.004` of the derived values and is easier to read in configuration; it
is not a different decision in any practical sense.

## Selected values

**`accept = 0.70`, `reject = 0.45`** — chosen by the project owner.

These are **not** the rule's output. The differences and their effects, as
measured against this corpus:

| | rule output | selected | effect |
|---|---|---|---|
| `accept` | 0.6538 | **0.70** | `+0.046` — a stricter pass gate |
| `reject` | 0.4506 | **0.45** | `−0.001` — effectively identical |

**`reject = 0.45` is safely above `H = 0.3491`**, so no off-topic seed can
reach the REJECT band. That constraint is what the rule protects; it holds.

**`accept = 0.70` moves the pass gate materially.** The rule bounds
`accept` by `L = 0.7553` — the lowest-scoring on-topic seed. At `0.70` that
seed still passes, but with only `0.055` of headroom instead of `0.102`.
Two consequences worth stating plainly:

- On-topic posts that resemble the weakest seed land in **review** rather
  than passing automatically. This is a deliberate tightening: more human
  review, fewer automatic publishes.
- The margin to `L` is roughly halved, so a future seed corpus that scores
  slightly lower could push genuine on-topic content into review. Widening
  the seed corpus is the fix, not lowering the threshold back.

At the selected thresholds the committed fixtures score:

| fixture | label | score | band |
|---|---|---|---|
| `on-lentil-curry` | on-topic | 0.8170 | PASS |
| `on-pho-chay` | on-topic (Vietnamese) | 0.9147 | PASS |
| `off-crypto` | off-topic | 0.1332 | REJECT |
| `off-jobs-vi` | off-topic (Vietnamese) | 0.1550 | REJECT |
| `off-japan-travel` | neutral | 0.4994 | REVIEW |
| `mix-steakhouse` | neutral (food-adjacent) | 0.5387 | REVIEW |

Both on-topic fixtures pass, both unambiguous spam fixtures reject, both
neutrals review. The banding behaves as intended.

## Enabling the filter

`app.filter.enabled` now defaults to **`true`** (`FILTER_ENABLED`, still
overridable per environment). This was the last step of re-enabling
moderation: the original workaround set it `false` because the filter
could not call HuggingFace, and leaving it off while the provider worked
would have defeated the point of the migration.

The operational consequence is that **every publish now makes a Gemini
call**. If that call fails the publish request still succeeds — the post
is queued and the outbox retries — but a prolonged Gemini outage means
posts sit `PENDING` until the 24h sweep moves them to `NEEDS_REVIEW`.
`EMBED_API_KEY` must therefore be set in any environment where the filter
is left enabled. Setting `FILTER_ENABLED=false` remains available as a
deliberate outage mode; it bypasses the filter entirely rather than
failing posts.

`FilterDefaultsTest` pins both the `FilterProperties` default and the
`${FILTER_ENABLED:true}` placeholder, so flipping this back requires a
visible test change rather than a quiet one-line edit.

## Provider properties verified at calibration time

Measured live while producing these centroids:

| property | value | significance |
|---|---|---|
| dimension | `768` | matches order 5's `vector(768)` column |
| L2 norm of returned vector | `0.999999989` | client normalisation works; the raw value would be ≈ `0.59` |
| determinism (same text, two calls) | cosine `1.000000000000` | centroids are reproducible across runs |
| cross-lingual on-topic cosine | `0.905370` | Vietnamese and English still align |
| on-topic vs off-topic cosine | `0.704609` | the two classes separate without centering |

Determinism was the largest open risk: if the API returned slightly
different vectors per call, committed centroids could not be reproduced
and the whole committed-artefact design would fail. It does not.

## Caveats

- **`L − H` is a two-sample statistic.** It is the single worst on-topic
  seed versus the single best off-topic seed, so it is sensitive to which
  seeds are in the corpus. `0.4063` is a property of *this corpus under
  this model*, not a general quality measure of Gemini embeddings.
- **The review band is wide.** At `0.70` / `0.45` the band spans `0.25`,
  which is a large share of human moderation. `off-japan-travel` at
  `0.4994` shows harmless non-food content landing mid-review. Queue
  volume, not correctness, is the cost.
- **Re-arming HuggingFace invalidates all of this.** See ADR-014: changing
  `app.filter.embedding.provider` requires regenerating the centroids,
  because they are one model's vectors.

## Related

- [ADR-014: Serve Filter Embeddings From Gemini](../adrs/014-embedding-provider-gemini.md)
- [ADR-007: Filter Post Content Before Publication](../adrs/007-post-content-filtering.md)
- [Feature spec: Embedding Provider Migration](embedding-provider-migration.md)
- [Business rule: post content filtering](../brs/posts.md)
