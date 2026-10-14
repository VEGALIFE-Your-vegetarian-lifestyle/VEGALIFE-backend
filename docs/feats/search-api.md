# Feature Spec: Search Posts, Videos, and Recipes

## Status

In progress

## Author / owner

Backend team; driving issue: [#40](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/40)

## Summary

Provide one public search endpoint that returns relevant posts, videos, and recipes with type, date, and category filters, pagination, and safe text highlights.

## Problem / motivation

The backend has separate storage and read paths for posts, uploaded videos, and recipes, but no endpoint that helps a visitor discover all three with one query. The frontend otherwise has to issue separate requests, combine pages, rank results, and highlight matches itself.

## Goals

- Search across public posts, successful videos attached to public posts, and active recipes with one query.
- Rank matches by text relevance, favoring titles and names over descriptive text.
- Support type, date range, category, and global pagination filters.
- Return highlighted title and snippet text with matches marked using escaped source text.

## Non-goals

- Personalized ranking, recommendations, or semantic/embedding search.
- Searching private, unpublished, hidden, failed, uploading, or deleted content.
- Returning complete post content, recipe instructions, or media metadata from search results.
- Adding a third-party search service.

## Requirements

### Functional Requirements

- [ ] FR-001: `GET /api/search` accepts a required non-blank query `q` of at most 100 characters.
- [ ] FR-002: Results may include `POST`, `VIDEO`, and `RECIPE` items in one globally paginated result set.
- [ ] FR-003: A post is searchable only when it is non-deleted and `published`.
- [ ] FR-004: A video is searchable only when its media row is non-deleted, has `status=succeed`, has a `video/*` MIME type, and is attached to at least one non-deleted published post.
- [ ] FR-005: A recipe is searchable when it is non-deleted. A category filter matches a recipe or video only through an attached non-deleted published post carrying that category.
- [ ] FR-006: Optional `type` restricts results to one type. Optional `from`/`to` filters use post `publishedAt`, and video/recipe `createdAt`; endpoints include both boundaries.
- [ ] FR-007: Optional `categoryId` filters posts by their category and videos/recipes through their published-post association.
- [ ] FR-008: Results use deterministic weighted substring relevance: exact title/name (`1.00`), title/name prefix (`0.90`), title/name substring (`0.80`), descriptive field (`0.65`), then body/instructions (`0.45`); ties use date descending and ID ascending.
- [ ] FR-009: Each result includes `highlightedTitle` and `highlightedSnippet` with `<mark>` around matches; source content is HTML-escaped before marks are inserted.
- [ ] FR-010: Pagination is applied after aggregation/ranking across all selected types, with a default page size of 20 and maximum of 100.
- [ ] FR-011: Invalid query, type, date range, or pagination returns the standard 400 failure response.

### Non-Functional Requirements

- [ ] NFR-SEC-001: Search is public, but only returns content meeting the visibility rules above; it never returns hidden, unpublished, failed, uploading, or soft-deleted records.
- [ ] NFR-MAINT-001: Use the existing PostgreSQL database stack; do not add a hosted search dependency or full-text-search extension.
- [ ] NFR-PERF-001: Query and count operations apply filters in PostgreSQL and do not load all matching entities into application memory before pagination.

## Design overview

Add a public `GET /api/search` endpoint, a validated request model, an aggregate result DTO, and a repository path that queries the existing `post`, `media`, `recipe`, `dish`, and join tables. The weighted substring relevance score and pagination operate across the combined result set in PostgreSQL. Videos are media rows and are exposed only through a published post association. Category filters for videos and recipes use their published-post associations. Search highlights escape source text before adding `<mark>` tags and limits snippets to 200 characters.

## Success metrics

At release, automated API tests verify results from all three types, global relevance ordering, each filter, global pagination, highlighted matches, and exclusion of non-public content.

## Acceptance criteria

**As a** visitor, **I want to** search across posts, videos, and recipes in one place, **so that** I can discover relevant vegetarian content without combining separate lists.

- [ ] Given a query matching records in all three content types, when search runs, then results from all matching types are returned in one globally ranked page.
- [ ] Given a type, date range, or category filter, when search runs, then only matching records are included and pagination totals reflect the filtered aggregate.
- [ ] Given matching content, when search runs, then title and snippet highlights identify matching text without allowing source HTML to become executable markup.
- [ ] Given unpublished/deleted posts, failed/uploading/deleted/unattached videos, or deleted recipes, when search runs, then those records are excluded.

## Risks / open questions

- Substring matching does not tokenize terms or perform stemming; a future product decision may add multilingual full-text search if needed.
- Search results intentionally expose a short recipe summary, not its full instructions; full recipe visibility may need a separate public recipe-detail rule.

---

## Related

- API reference: [`GET /api/search`](../apis/search/get-search.md)
- Visibility rule: [`BR-SEARCH-002`](../brs/search.md)
- Driving issue: [#40](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/40)
