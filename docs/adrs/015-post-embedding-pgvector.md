# ADR-015: Store Post Embeddings in pgvector and Search Them in SQL

## Status

Accepted — adds a second, independent use of cosine similarity. It does not
touch [ADR-007](007-post-content-filtering.md) or the filter's scorer.

## Date

2026-10-18

## Deciders

Project owner (storage and disposition decided during post-demo triage),
backend implementation.

## Context

The content filter scores a post against two topic centroids on the
classpath ([ADR-007](007-post-content-filtering.md)). It never compares
two posts to each other, and it never persists the post's vector — the
embedding is computed on every publish by
`ContentFilterOutboundAdapter` → `ContentFilterService` →
`embeddingModel.embed(...)`, used for scoring, and discarded.

Reviewer feedback on the demo asked for the other comparison: posts that
duplicate each other should be caught and sent to a human. That needs
vectors **retained across posts**, which nothing in the current design
does.

Three constraints shaped the storage decision:

- **The vector is already computed.** Persisting it costs zero extra
  embedding calls; the only question is where it goes.
- **The comparison target is a whole table, not two constants.**
  "Which published post is closest to this one?" is a nearest-neighbour
  query over an unbounded and growing set.
- **The embedding model changes.** ADR-014 changed it once already, and
  vectors from different models are not comparable.

## Decision

**Persist each post's raw embedding in a `post_embedding` table and run
the similarity search in SQL, using the pgvector extension with an HNSW
cosine index.**

Specifically:

1. `post_embedding` holds `post_id`, `embedding VECTOR(768)`, `model`,
   and `created_at`.
2. The search is `ORDER BY embedding <=> $1 LIMIT 1` — the `LIMIT` is
   load-bearing, not an optimisation; without it pgvector does not use
   the index.
3. **The table is deliberately not a JPA entity.** pgjdbc surfaces
   `vector` as text; an entity would need a custom Hibernate `BasicType`
   for a read path that never needs the raw vector back (only a post id
   and a score). The write path binds the array as text with a `::vector`
   cast, which is the documented pgvector idiom.
4. **`model` is a column, not an assumption.** Rows whose `model`
   differs from the active configuration are skipped, never compared.
5. **Stored vectors are the raw `embed()` output**, not the mean-centered
   vector the filter scores with. Post-to-post similarity must not be
   contaminated by the seed-corpus mean.

Above-threshold matches are **always routed to admin review**. There is
no auto-reject tier: a false positive on Vietnamese text is plausible
enough that a model's opinion must not destroy a member's post.

## Alternatives considered

- **`REAL[]` column with cosine computed in Java.** Rejected. It makes
  every detection a full table scan in application code, and the scan
  grows with the platform. It was the earlier answer and was superseded
  once pgvector was confirmed available on both candidate hosts at no
  extra cost.
- **Reusing the filter's `EmbeddingRelevanceScorer.cosine()` in SQL.**
  Not possible, and worth recording so it is not attempted later. The
  filter's centroids live in `centroids.json` on the classpath, so there
  is no table to index, and the scorer **mean-centers both sides**
  (`subtract(embedding, mean)`), which `<=>` cannot express. Moving the
  filter onto pgvector would silently change every verdict.
- **A JPA entity for the table.** Rejected — see decision 3.
- **Keeping vectors in memory.** Rejected: they must survive restarts and
  be shared across instances.

## Consequences

**This does not replace the filter's cosine.** pgvector adds a second,
independent use of cosine (post ↔ post). The filter's first use
(post ↔ centroid) is untouched and still runs twice per publish.

**`VECTOR(768)` is a hard coupling to `EMBED_DIMENSIONS`.** Changing the
embedding dimension requires a migration that rebuilds the column and
every row in it. The two must not drift silently.

**Approximate search has a recall trap.** HNSW applies a `WHERE` clause
*during* traversal, which can return fewer matches than exist, with no
error and no warning. A non-issue at this corpus size; the documented
fixes are a partial index, a plain index on the filter column, or
partitioning.

**Hosts do not ship pgvector by default.** Available on Render and Neon
at no extra cost, but `CREATE EXTENSION` may need a role without
permission, and the extension must be enabled per database. This is a
deployment prerequisite, not an assumption the code may make.

**The test suite must run on an image that has the extension.** 41
integration test files hardcode a Postgres image that does not, so the
image moved to a pgvector build before the migration existed.

**Reverting ADR-014 after this ships is now a data hazard.** It would
change the embedding model, leaving every stored vector in the old space.
The `model` column makes this detectable rather than silent; the
operational answer is to regenerate `post_embedding`, not to hope.

## Related

- [ADR-007: Filter Post Content Before Publication](007-post-content-filtering.md)
- [ADR-011: Keep Post Visibility in `status` and the Filter Verdict in `flag` Only](011-post-status-and-filter-axis-separation.md)
- [ADR-014: Serve Filter Embeddings From Gemini](014-embedding-provider-gemini.md)
- [Feature spec: Duplicate Post Detection](../feats/duplicate-post-detection.md)
