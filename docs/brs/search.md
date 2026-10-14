# Business Rule: Public Search Visibility and Ranking

## Rule ID

`BR-SEARCH-002`

## Status

Active

## Statement

Public search includes only non-deleted published posts, non-deleted successful video media attached to at least one non-deleted published post, and non-deleted recipes. Search does not return unpublished, hidden, failed, uploading, soft-deleted, or unattached video content. A category filter for a video or recipe is satisfied only by a linked non-deleted published post carrying that category. Results use weighted, case-insensitive substring matching: exact title/name (`1.00`), title/name prefix (`0.90`), title/name substring (`0.80`), descriptive fields (`0.65`), then body/instructions (`0.45`). Pagination is applied to the globally ranked result set, and highlighted snippets are limited to 200 characters with source HTML escaped before marking matches.

## Rationale

Search is a public discovery surface across content types. It must not reveal content that is private or still under moderation, and the same category relationship used to contextualize video and recipe attachments should determine category-filter matches.

## Scope & Exceptions

Applies to the public `GET /api/search` endpoint. It does not change direct recipe ownership, recipe mutation, post detail, or administrator listing rules.

## Enforcement

- `SearchService` and the aggregate PostgreSQL search query enforce visibility, category, ranking, and pagination rules.
- `SecurityConfig` permits public GET access to `/api/search`.
- API reference: `docs/apis/search/get-search.md`.

## Last Reviewed

2026-10-13, by Vegalife backend team
