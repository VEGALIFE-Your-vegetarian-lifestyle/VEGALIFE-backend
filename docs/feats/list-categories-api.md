# Feature Spec: List Categories API

## Status
Implemented

## Author / owner
Backend team (Huy); tracked in Sprint plan (VEGALIFE-Sprint.xlsx, "Vegalife BE" row 36, item 34.0, actor "All").

## Summary
Let any caller — logged in or anonymous — retrieve active content categories, paginated and optionally filtered by name, so the browse/search UI can render category chips and the create-post screen can populate its category selector (per `SWP391-VEGALIFE.docx` Screen 01 and Screen 05 mockups).

## Problem / motivation
Categories can now be created, edited, and retired (Sprint items 31.0–33.0), but there is still no way for a client to read them back. Unlike the other category endpoints, this one's actor is "All" (not Admin) — it's a public read used to power category browsing/filtering for every visitor, matching the existing public pattern for `GET /api/users/{userId}/posts`. It follows the same request/response shape as the other list endpoints in this codebase (`GET /api/admin/users`, `GET /api/posts`) rather than a bare array, for consistency.

## Goals
- Any caller (no JWT required) can list active categories, paginated.
- A `name` query param filters categories by a case-insensitive substring match.
- Retired (soft-deleted) categories are excluded.
- Results default to sorted by name ascending; `sort` accepts other properties/directions, matching `UserListRequest`'s convention.

## Non-goals
- Returning post counts per category or any other aggregate.
- Exposing retired categories, even to Admins, through this endpoint (an Admin needing that view is a separate, not-yet-scoped concern).
- Filtering by anything other than name (e.g. by usage/post count) — not requested.

## Requirements

### Functional Requirements
- [x] FR-001: `GET /api/categories` requires no authentication.
- [x] FR-002: Supports `page` (default 0), `size` (default 20, max 100), and `sort` (default `name,asc`), matching `UserListRequest`'s conventions.
- [x] FR-003: Supports an optional `name` query param: a case-insensitive substring filter on category name.
- [x] FR-004: Response is 200 with a `PageResponse<CategoryResponse>` (`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`) of active (`deletedAt IS NULL`) categories matching the filter.
- [x] FR-005: Each item has the same shape as the create/edit responses (`id`, `name`, `description`, `createdAt`).
- [x] FR-006: No categories match → 200 with an empty `content` array (not 404).
- [x] FR-007: Invalid `page`/`size`, or a malformed `sort` → 400.

### Non-Functional Requirements
- [x] NFR-PERF-001: Single indexed query per page (`idx_category_active` covers `deleted_at IS NULL`; the `name` filter is a `LIKE` scan, acceptable at the category table's expected size); no N+1 risk since the response carries no related entities.
- [x] NFR-MAINT-001: Response shape uses the existing `ApiResponse<T>` and `PageResponse<T>`; no new wrapper styles, and consistent with every other "list" endpoint in this codebase.

## Design overview
New `CategoryController` (distinct from `AdminCategoryController`, since this endpoint isn't Admin-only) at `/api/categories`, backed by `CategoryService.listCategories(CategoryListRequest)`. A new `CategoryListRequest` DTO mirrors `UserListRequest`'s `page`/`size`/`sort` getters (with defaults) plus a `name` filter field. A new `CategorySpecifications.activeWithNameFilter(name)` (mirroring `UserSpecifications`) combines the `deletedAt IS NULL` predicate with an optional case-insensitive `LIKE` on `name`. `CategoryRepository` now also extends `JpaSpecificationExecutor<Category>`. The service parses `sort` the same way `AdminService.parseSort` does (throwing `ValidationException` on a malformed value), builds a `Pageable`, and maps the resulting `Page<Category>` through `CategoryMapper::toResponse` into a `PageResponse<CategoryResponse>` (`PageResponse.from`). `SecurityConfig` gets an explicit `permitAll()` for `GET /api/categories`, alongside the existing `GET /api/users/*/posts` public rule.

## Success metrics
- Sprint item 34.0 ("List categories API") delivered; the category selector (Screen 05) and category filter (Screen 01) mockups have a real, paginated, filterable endpoint to read from.

## Acceptance criteria
**As** any visitor (logged in or not), **I want to** browse and search active categories, **so that** I can filter content by category, or pick one when creating a post, even as the category list grows.

- [x] Given no JWT, when calling the endpoint with no params, then 200 with the first page of active categories, sorted by name ascending.
- [x] Given a retired category exists, when calling the endpoint, then it is excluded from every page.
- [x] Given a `name` filter, when calling the endpoint, then only categories whose name contains it (case-insensitive) are returned.
- [x] Given `page`/`size` params, when calling the endpoint, then the requested page and page size are returned with correct pagination metadata.
- [x] Given no categories match, when calling the endpoint, then 200 with an empty `content` array.
- [x] Given an invalid `size` (e.g. 0), when calling the endpoint, then 400.

## Related
- API Reference: `docs/apis/post/get-categories.md`
- Companion: `docs/feats/create-category-api.md`, `docs/feats/edit-category-api.md`, `docs/feats/delete-category-api.md`
- Consumer: `docs/apis/post/post-posts.md` (categoryIds)
