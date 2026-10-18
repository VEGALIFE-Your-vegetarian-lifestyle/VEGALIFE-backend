# Feature Spec: Duplicate Post Detection

## Status

In Progress — issue
[#156](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/156).

## Problem

The content filter judges whether a post is *on topic*. It does not judge
whether a post is **someone else's post again**. A member can copy an
existing post almost verbatim and it publishes cleanly, because the filter
compares it against two topic centroids and a copy of an on-topic post is,
by construction, perfectly on topic.

Demo review asked for exactly this gap to be closed: posts that duplicate
each other should be flagged and sent to a human.

## Goal

Two posts that say the same thing are caught, and the second one goes to
the admin review queue instead of publishing. The cost is **zero extra
embedding calls** — the vector is already computed for the topic filter.

## Non-goals

- **Media moderation.** Out of scope for the whole hardening round.
- **Recipes, dishes and ingredients as comparison text.** The compared text
  stays `title + content`. Extending the filter *into* one post is a
  separate feature.
- **Replacing the topic filter.** See [ADR-015](../adrs/015-post-embedding-pgvector.md):
  pgvector adds a second, independent use of cosine.
- **Auto-reject.** There is no tier that silently refuses a duplicate.
- **Backfilling history.** Decided below, deliberately.
- **Migrating off Render, or adopting `render.yaml`.**

## What is compared

`post.title + "\n" + post.content`, the same text the topic filter scores.

The embedding stored is the **raw output of `embeddingModel.embed(...)`**,
not the mean-centered vector the topic filter scores against. Post-to-post
similarity must not carry the seed-corpus mean.

## Decisions

| # | Decision | Answer | Consequence |
|---|---|---|---|
| **D2** | Disposition above the threshold | **Always route to admin.** No auto-reject tier | A false positive costs a review, not a deleted post |
| **D4** | Storage and search | **pgvector `vector(768)` + HNSW cosine index; search in SQL** | Detection is a nearest-neighbour query, not an application-side scan. Recorded in [ADR-015](../adrs/015-post-embedding-pgvector.md) |
| **D6** | Backfill of posts published before this ships | **None — start from launch** | Historical posts are **permanently invisible** to detection. Stated plainly below |
| **D7** | Similarity threshold | **`0.85`**, configurable | Measured against real Vietnamese/English pairs; the bands are below and in the PR |

### D6 in plain terms

Posts published before this feature ships have **no stored vector**, so
nothing can ever match them. There is no deferred job and no "catch up
later" — the comparison set is empty on day one and grows only with posts
published afterwards.

This means:

- Detection does nothing until the first post publishes, and is only
  useful once real content exists.
- Any plagiarism already in the database stays undetected, permanently.
- The gap is a **decision, not a bug.** Adding a backfill later is a
  separate job that needs no schema change; the two are deliberately
  decoupled.

### D7 and why the number is measured

`0.85` is a **cosine similarity**, not a distance. It was measured against
the live `gemini-embedding-001` model rather than assumed, because the
mapping from a reviewer's "80–90%" to a vector score is unreliable in
both directions.

**Measured bands** (768 dimensions, `SEMANTIC_SIMILARITY`, L2-normalised,
real Vietnamese and English post text):

| Pair | Similarity |
|---|---|
| Near-identical body, one character changed | `0.985` |
| Same post, one character added | `0.964` |
| Same post, embellished | `0.952` |
| Body vs its English translation | `0.900` |
| Same topic, rewritten | `0.894` |
| **Lowest duplicate measured** — Vietnamese title vs its English translation | **`0.753`** |
| **Highest non-duplicate measured** — different post, same topic | **`0.832`** |
| Different post, related topic | `0.789` |
| Different topic | `0.721` |

The two bands **overlap**: the weakest duplicate (`0.753`) scores below the
strongest non-duplicate (`0.832`). No single threshold separates them, so
the value trades precision against recall deliberately.

| Threshold | Duplicates caught | Distinct pairs wrongly flagged |
|---|---|---|
| `0.80` | 9 / 11 | 1 / 6 |
| **`0.85`** | **6 / 11** | **0 / 6** |
| `0.90` | 4 / 11 | 0 / 6 |

The sample is small — 11 duplicate pairs and 6 distinct pairs — so the
table bounds the problem rather than solving it. It is enough to show that
`0.8` does **not** separate the two classes, which is what it was
meant to establish.

**`0.85` is the lowest value with zero false flags** across every pair
measured, including the full Vietnamese-to-English distinction at `0.757`.
Chosen by the project owner.

**What it misses, on purpose.** Translated reposts and light rewrites
below `0.85` are not caught. That is the accepted trade: a missed duplicate
costs nothing, while a false flag costs a member their publish. The misses
are visible in the table rather than hidden behind an average.

## Behaviour

On a publish:

1. The filter computes the post's embedding — **once**, as it does today.
2. Before the vector is stored, the detector searches for the nearest
   **published** post with the same `model`, excluding the candidate.
3. If similarity ≥ threshold → the post is flagged as a duplicate,
   `status = unpublished`, nothing is published, and a WARN records both
   post ids and the score.
4. Otherwise → the existing topic verdict stands, and the vector is
   upserted.

### Comparison targets

Only posts that are **published and not deleted** are targets. Drafts,
unpublished posts, and rejected posts are never compared against —
otherwise every edit of one's own post would flag itself.

Because a flagged duplicate never publishes, it also never becomes a
target. **Chain duplicates are therefore missed by design**: if A
publishes and B (≈ A) is flagged, a later C that matches B but not A is
not caught. This is conservative and accepted.

### Why the search must exclude the candidate

The candidate's vector is stored *after* the check. Storing first would
make every post match itself at similarity `1.0`. Re-publishing a post
upserts the same row rather than adding a second one.

## Schema

### `V35` — storage

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE post_embedding (
  post_id    UUID PRIMARY KEY REFERENCES post(id),
  embedding  VECTOR(768)  NOT NULL,
  model      VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

With an HNSW cosine index and supporting indexes on `model` and
`created_at`.

**`VECTOR(768)` is coupled to `EMBED_DIMENSIONS`.** Changing the
embedding dimension requires a migration rebuilding this column and every
row in it. Recorded so the two never drift silently.

**`model` is the isolation mechanism.** Vectors from different embedding
models are not comparable. Rows whose `model` differs from the active
configuration are skipped — never compared. Without this column, the next
provider migration would silently produce meaningless similarities while
looking perfectly healthy.

### `V36` — the flag value

`post.flag` carries a `CHECK` constraint (`V19`) listing its four legal
values, so a Java enum change alone is not enough. `V36` replaces that
constraint with one that also admits the duplicate value.

## Configuration

| Key | Default | Meaning |
|---|---|---|
| `app.filter.duplicate-threshold` | `0.85` | Cosine **similarity** at or above which a post is a duplicate |
| `app.filter.duplicate-enabled` | `true` | Turns detection off without disabling the topic filter |

## Deployment prerequisites

- **pgvector must be enabled on the target database.** It is not on by
  default on Render or Neon, though available on both at no extra cost.
  `CREATE EXTENSION` may require a role without permission; the fallback
  is running it once through the host's SQL console. **Verify on the
  target database; do not assume.**
- **The test suite needs an image containing the extension.** 41
  integration test files hardcoded a Postgres image that does not ship
  pgvector; they move to a pgvector build before the migration exists,
  and the **whole** integration suite must pass against it — that is the
  real proof, since 41 unrelated tests now boot on a different container.

## Acceptance criteria

- [x] A post similar to an existing published post above the threshold is
      not published; it is flagged and appears in the admin queue.
- [x] A genuinely different post publishes normally.
- [x] The author's own drafts, unpublished posts and rejected posts are
      never comparison targets.
- [x] Rows with a different `model` are ignored, proven by a test.
- [x] Exactly one embedding call per publish.
- [x] Re-publishing a post upserts its vector and does not match itself.
- [x] The whole existing integration suite passes on the new image.
- [ ] `EXPLAIN (ANALYZE)` shows an index scan on the HNSW index, not a
      sequential scan; output recorded in the PR.
- [x] The threshold is measured against real Vietnamese and English
      text, with observed scores recorded.
- [ ] `./mvnw clean verify` green, observed in the session that ships it.

## Known limitations

- **HNSW recall under a `WHERE` clause.** The filter is applied *during*
  traversal, which can return fewer matches than exist, with no error. A
  non-issue at this corpus size; the documented fixes are a partial
  index, a plain index on the filter column, or partitioning.
- **Chain duplicates are missed** — see "Comparison targets".
- **History is not covered** — see D6.
- **Threshold sensitivity.** A lightly-edited repost may fall under the
  threshold. That is the intended trade: missing a duplicate costs
  nothing, flagging a genuine post costs a member their publish.

## Testing

- **Unit**: threshold comparison in Java (above, at exactly the
  threshold, just below), candidate exclusion, model mismatch ignored.
- **Integration**: publish A, publish a near-copy of B → flagged and
  queued; publish an unrelated post → `PASSED`. Runs against a real
  Postgres with pgvector, not a mock.

## Rollback

- `V35` and `V36` are the only schema effects. The extension can remain
  enabled; the table and constraint need a compensating migration, never
  an edit to an applied one.
- Detection is disabled by `app.filter.duplicate-enabled=false` without
  touching the topic filter, and does not require a rollback.
- Reverting
  [ADR-014](../adrs/014-embedding-provider-gemini.md) after this ships
  leaves stored vectors in the old space. The `model` column makes them
  skipped rather than silently compared; the operational answer is to
  regenerate `post_embedding`.

## Related

- [ADR-015: Store Post Embeddings in pgvector and Search Them in SQL](../adrs/015-post-embedding-pgvector.md)
- [ADR-007: Filter Post Content Before Publication](../adrs/007-post-content-filtering.md)
- [ADR-011: Keep Post Visibility in `status` and the Filter Verdict in `flag` Only](../adrs/011-post-status-and-filter-axis-separation.md)
- [ADR-014: Serve Filter Embeddings From Gemini](../adrs/014-embedding-provider-gemini.md)
- [Business rule: post content filtering](../brs/posts.md)
