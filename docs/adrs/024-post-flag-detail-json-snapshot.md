# ADR-024: Store the Post Blocker Detail as a Typed JSON Snapshot

## Status

Accepted

## Date

2026-11-13

## Deciders

Project owner, backend implementation.

## Context

ADR-023 gave `post.flag` a closed vocabulary (`FILTER_FAILED`,
`SIMILAR_CONTENT`, `FILTER_SKIPPED`) and parked the evidence for it in one new
column, `post.flag_detail VARCHAR(64)` (migration `V56`). That column was
written by three unrelated call sites and each one invented its own shape:

- `FILTER_FAILED` — `score=0.312`
- `SIMILAR_CONTENT` — `SIMILAR_CONTENT_POST_<postId>`
- `FILTER_SKIPPED` — `FILTER_DISABLED`, `FILTER_SWEPT`, or
  `FILTER_UNAVAILABLE: <classified provider cause>`

Four problems follow from that:

1. **The value cannot be read without knowing the flag.** A reader has to
   branch on `flag` before it can parse `flag_detail`, and nothing in the
   database or the API schema says which shape belongs to which flag.
2. **It truncates exactly when it matters.** `VARCHAR(64)` already cuts
   `FILTER_UNAVAILABLE: timeout (HTTP 504) upstream_timeout` (60 characters)
   close to the limit; any longer provider code is silently truncated, and the
   truncated string is the only trace on the post row.
3. **The facts a reviewer needs were thrown away.** The duplicate comparison
   produces a cosine similarity and applies a threshold, and the relevance
   scorer produces a score against an accept/reject boundary — but only the
   score reached the row, and only as text. A reviewer could see that a post
   was blocked, not how close it was to the line.
4. **The bar can move after the fact.** `content_filter_accept_threshold` and
   `content_filter_reject_threshold` are live platform settings, so a stored
   score loses its meaning the moment an administrator retunes them; nothing
   recorded which threshold was in force for that run.

The free text was the aggravating factor, not the evidence: the full classified
cause is already written to `moderation_log.reason` (append-only, unbounded),
so the copy on the post row only duplicated it badly.

## Decision

**`post.flag_detail` is a `JSONB` object, one shape per blocker, always carrying
`kind`, which must equal `flag`.**

```
FILTER_FAILED    {"kind":"FILTER_FAILED","outcome":"FAIL","score":0.312,"threshold":0.45}
SIMILAR_CONTENT  {"kind":"SIMILAR_CONTENT","postId":"<uuid>","similarity":0.9712,"threshold":0.85}
FILTER_SKIPPED   {"kind":"FILTER_SKIPPED","cause":"DISABLED"|"SWEPT"|"UNAVAILABLE"}
```

1. **`kind` is the discriminator and is redundant with `flag` on purpose.**
   The database enforces `flag_detail->>'kind' = flag`, so the snapshot and the
   blocker it qualifies can never describe different things.
2. **`threshold` is a snapshot of the boundary actually applied**, not a
   pointer to current settings: the accept threshold for `PASS`/`REVIEW`, the
   reject threshold for `REJECT`, the duplicate threshold for
   `SIMILAR_CONTENT`. It is omitted when the caller cannot know it.
3. **`similarity` is stored.** It is already computed by the duplicate lookup
   (`1 - (embedding <=> ?::vector)`); keeping it costs nothing and turns "this
   is a duplicate" into "this is a 97% duplicate".
4. **`FILTER_SKIPPED` keeps only a cause code.** The prose lands in
   `moderation_log.reason`, which is where an operator already looks.
5. **The API exposes the object, not the raw text** (`PostFlagDetailResponse`);
   absent fields are omitted rather than sent as `null`.
6. **`content_score` is unchanged** and remains the canonical last relevance
   score, including after publication, when `flag_detail` is cleared.

## Considered options

- **Option A — typed side columns** (`flag_score`, `flag_threshold`,
  `flag_matched_post_id`, `flag_cause`). Queryable and typed, but every future
  blocker adds a column to `post`, most of them null on every row, and the
  field set of one blocker (`similarity`) has no home in a shared schema.
- **Option B — JSONB object with a `kind` discriminator (chosen).** One
  column, one shape rule, evidence that varies by blocker without schema churn,
  and Postgres can still index or filter any field later.
- **Option C — keep the string and add one column per missing fact.** Cheapest
  migration, but leaves the truncated free text in place and keeps three
  unparseable shapes in the row.
- **Option D — drop `flag_detail` and rely on `moderation_log` alone.** No
  admin API exposes the log, so a reviewer would have to query the database to
  answer "what blocked this post". Rejected as a regression in moderation UX.

## Consequences

**Positive**

- Any reader — service, admin UI, ad-hoc SQL — can parse the detail from
  `kind` alone, and the columns cannot disagree.
- Scores are interpretable after the fact: the snapshot carries the bar.
- The truncation class of bug is gone; nothing writes free text to the row.
- Adding a blocker detail is a migration of data, not of columns.

**Negative / trade-offs**

- **`flag_detail` is not strongly typed in the database.** `CHECK` constraints
  enforce "is an object" and "kind matches flag", not per-blocker field types;
  that contract lives in `PostFlagDetail` and `PostFlagDetailResponse`.
- **Legacy rows stay incomplete.** `V57` maps the old strings but cannot invent
  a threshold, similarity or outcome that was never persisted, so rows blocked
  before it keep only `kind` + score/id/cause.
- **Two vocabularies for the same fact.** `flag` and `flag_detail->>'kind'` must
  be written together; the `CHECK` turns a mistake into a failed write rather
  than a silent inconsistency.
- **The free text is no longer on the post row.** An operator who only looks at
  the post sees `UNAVAILABLE`; the provider detail requires reading
  `moderation_log`, which no admin API exposes yet.

## Verification

- `V57` applies on a fresh database: every integration context runs Flyway from
  empty, so the column type and the three constraints are proven.
- The legacy-row backfill is **not** covered by an automated test — it needs a
  database that predates `V57`. It translates only what the old strings
  contained (`score=<x>`, `SIMILAR_CONTENT_POST_<id>`, and the
  `FILTER_DISABLED`/`FILTER_SWEPT`/`FILTER_UNAVAILABLE` prefixes) and was
  reviewed against the writers that produced them in `V56`.
- `PostEmbeddingSchemaIntegrationTest` proves that a snapshot whose `kind`
  disagrees with `flag`, and a non-object snapshot, are both rejected by the
  database.
- Revisit this decision if a blocker needs a queryable field in a hot predicate
  (then promote that field to a column) or if an admin API starts exposing
  `moderation_log`, which would make the post-row copy redundant again.
