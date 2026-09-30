# Feature Spec: List Categories API

## Status
Implemented

## Author / owner
Backend team (Huy); tracked in Sprint plan (VEGALIFE-Sprint.xlsx, "Vegalife BE" row 36, item 34.0, actor "All").

## Summary
Let any caller — logged in or anonymous — retrieve the full list of active content categories, so the browse/search UI can render category chips and the create-post screen can populate its category selector (per `SWP391-VEGALIFE.docx` Screen 01 and Screen 05 mockups).

## Problem / motivation
Categories can now be created, edited, and retired (Sprint items 31.0–33.0), but there is still no way for a client to read them back. Unlike the other category endpoints, this one's actor is "All" (not Admin) — it's a public read used to power category browsing/filtering for every visitor, matching the existing public pattern for `GET /api/users/{userId}/posts`.

## Goals
- Any caller (no JWT required) can list every active category.
- Retired (soft-deleted) categories are excluded.
- Results are returned sorted by name for a stable, predictable UI order.

## Non-goals
- Pagination or filtering (the category set is small and enumerable — "Simple" complexity per the Sprint sheet; no functional requirement calls for it).
- Returning post counts per category or any other aggregate.
- Exposing retired categories, even to Admins, through this endpoint (an Admin needing that view is a separate, not-yet-scoped concern).

## Requirements

### Functional Requirements
- [x] FR-001: `GET /api/categories` requires no authentication.
- [x] FR-002: Response is 200 with every active (`deletedAt IS NULL`) category, sorted by `name` ascending.
- [x] FR-003: Each item has the same shape as the create/edit responses (`id`, `name`, `description`, `createdAt`).
- [x] FR-004: No categories exist → 200 with an empty array (not 404).

### Non-Functional Requirements
- [x] NFR-PERF-001: Single indexed query (`idx_category_active` already covers `deleted_at IS NULL`); no N+1 risk since the response carries no related entities.
- [x] NFR-MAINT-001: Response shape uses the existing `ApiResponse<T>`; no new wrapper styles.

## Design overview
New `CategoryController` (distinct from `AdminCategoryController`, since this endpoint isn't Admin-only) at `/api/categories`, backed by `CategoryService.listCategories()`, which calls a new `CategoryRepository.findByDeletedAtIsNullOrderByNameAsc()` and maps through a new `CategoryMapper.toResponseList`. `SecurityConfig` gets an explicit `permitAll()` for `GET /api/categories`, alongside the existing `GET /api/users/*/posts` public rule.

## Success metrics
- Sprint item 34.0 ("List categories API") delivered; the category selector (Screen 05) and category filter (Screen 01) mockups have a real endpoint to read from.

## Acceptance criteria
**As** any visitor (logged in or not), **I want to** see the list of active categories, **so that** I can browse or filter content by category, or pick one when creating a post.

- [x] Given no JWT, when calling the endpoint, then 200 with all active categories, sorted by name.
- [x] Given a retired category exists, when calling the endpoint, then it is excluded from the response.
- [x] Given no categories exist, when calling the endpoint, then 200 with an empty array.

## Related
- API Reference: `docs/apis/post/get-categories.md`
- Companion: `docs/feats/create-category-api.md`, `docs/feats/edit-category-api.md`, `docs/feats/delete-category-api.md`
- Consumer: `docs/apis/post/post-posts.md` (categoryIds)
