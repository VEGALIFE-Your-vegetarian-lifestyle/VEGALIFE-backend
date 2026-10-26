# API Reference Documentation

Supplementary human-written API docs for the Vegalife backend (complements auto-generated OpenAPI/Swagger at `/swagger-ui.html`).

Each file documents one endpoint: `docs/apis/<feature>/<method>-<resource>.md`

Shared error/response contract: [`error-responses.md`](error-responses.md).

Rows are sorted alphabetically by endpoint path, not grouped by feature folder, so an admin operation and a member operation on the same resource sit next to each other. Rows that document no HTTP endpoint come last.

| File | Endpoint | Description |
|------|----------|-------------|
| `admin/post-categories.md` | `POST /api/admin/categories` | Create a content category (Admin) |
| `admin/patch-categories-categoryid.md` | `PATCH /api/admin/categories/{categoryId}` | Edit a content category (Admin) |
| `admin/delete-categories-categoryid.md` | `DELETE /api/admin/categories/{categoryId}` | Retire a content category (Admin) |
| `admin/get-comments.md` | `GET /api/admin/comments` | List all comments (Admin) |
| `admin/post-comments-commentid-moderate.md` | `POST /api/admin/comments/{commentId}/moderate` | Soft-delete a comment with a required reason (Admin) |
| `admin/get-dashboard.md` | `GET /api/admin/dashboard` | Aggregated, time-range-filtered platform metrics, cached (Admin) |
| `admin/dish-and-ingredient-crud.md` | `GET/POST/PATCH/DELETE /api/admin/dishes[/{dishId}][/approve\|/reject]` | Manage dish names, including review state; `admin/ingredients` mirrors it (Admin) |
| `admin/post-locations.md` | `POST /api/admin/locations` | Create a location (Admin) |
| `admin/patch-locations-locationid.md` | `PATCH /api/admin/locations/{locationId}` | Edit a location (Admin) |
| `admin/delete-locations-locationid.md` | `DELETE /api/admin/locations/{locationId}` | Soft-delete a location (Admin) |
| `admin/get-payments.md` | `GET /api/admin/payments` | List payments across all users with user/status/date filters (Admin) |
| `admin/plan-crud.md` | `GET/POST/PATCH/DELETE /api/admin/plans[/{planId}]` | Manage AI subscription plans, including soft-deactivation (Admin) |
| `admin/get-posts.md` | `GET /api/admin/posts` | List all posts across users and statuses (Admin) |
| `admin/post-posts-postid-moderate.md` | `POST /api/admin/posts/{postId}/moderate` | **Removed** — retained only as a record; replaced by approve/reject (ADR-016) |
| `admin/post-posts-postid-approve.md` | `GET/POST /api/admin/posts/{postId}[/approve\|/reject]` | Read a post's component detail, approve or reject it with its components (Admin) |
| `admin/get-recipes.md` | `GET /api/admin/recipes` | List all recipes across users with ingredients and instructions (Admin) |
| `admin/recipe-crud.md` | `GET/PATCH/DELETE /api/admin/recipes[/{recipeId}][/approve\|/reject]` | Manage recipes, including review state (Admin) |
| `admin/settings-crud.md` | `GET/PATCH /api/admin/settings` | Manage grouped platform settings, audited (Admin) |
| `admin/get-users.md` | `GET /api/admin/users` | List user accounts (Admin) |
| `admin/post-restore-user.md` | `POST /api/admin/users/{userId}/restore` | Restore suspended user account (Admin) |
| `admin/post-suspend-user.md` | `POST /api/admin/users/{userId}/suspend` | Suspend user account (Admin) |
| `admin/get-videos.md` | `GET /api/admin/videos` | List all uploaded videos across users and statuses (Admin) |
| `ai/get-conversations.md` | `GET /api/ai/conversations` | List own AI conversations, most recently active first (authenticated) |
| `ai/get-conversations-id.md` | `GET /api/ai/conversations/{id}` | Read one owned conversation's messages in order (authenticated) |
| `ai/post-messages.md` | `POST /api/ai/messages` | Send a chat message, get the AI reply (authenticated) |
| `ai/post-messages-stream.md` | `POST /api/ai/messages/stream` | Send a chat message, stream the AI reply as SSE (authenticated) |
| `auth/post-forgot-password.md` | `POST /api/auth/forgot-password` | Request password-reset OTP by email |
| `auth/post-google.md` | `POST /api/auth/google` | Sign in with a Google authorization code (creates/links account) |
| `auth/post-login.md` | `POST /api/auth/login` | User login |
| `auth/post-logout.md` | `POST /api/auth/logout` | User logout |
| `auth/post-refresh.md` | `POST /api/auth/refresh` | Refresh access token |
| `auth/post-register.md` | `POST /api/auth/register` | Register new user (sends verification OTP) |
| `auth/post-resend-email.md` | `POST /api/auth/resend-email` | Resend email-verification OTP (generic 200) |
| `auth/post-reset-password.md` | `POST /api/auth/reset-password` | Set new password after verification (step 2 of 2) |
| `auth/post-verify-email.md` | `POST /api/auth/verify-email` | Verify email with 6-digit OTP |
| `auth/post-verify-password-reset.md` | `POST /api/auth/verify-password-reset` | Verify password-reset OTP (step 1 of 2) |
| `post/get-categories.md` | `GET /api/categories` | List active content categories (public) |
| `recipes/get-dishes.md` | `GET /api/dishes` | List active dishes for recipe-create autocomplete (authenticated) |
| `recipes/get-dishes-suggestions.md` | `GET /api/dishes/suggestions` | Suggest plannable dishes with allergy-filtered recipe recommendations (authenticated) |
| `ingredients/get-ingredients.md` | `GET /api/ingredients` | List ingredients paginated, with case-insensitive name filter (authenticated) |
| `locations/get-locations.md` | `GET /api/locations` | List recommend locations with rating/type/price/open-now filters (authenticated) |
| `locations/get-locations-nearby.md` | `GET /api/locations/nearby` | List every active location within a radius, nearest first, no pagination (authenticated) |
| `media/post-upload.md` | `POST /api/media/upload` | Issue a signed upload grant and media ID |
| `media/get-media-mediaid.md` | `GET /api/media/{mediaId}` | Read a stored media record |
| `media/delete-media-mediaid.md` | `DELETE /api/media/{mediaId}` | Soft-delete a media record (owner or Admin); physical purge is async |
| `media/post-media-mediaid-confirm.md` | `POST /api/media/{mediaId}/confirm` | Verify a completed upload and finalize the media record |
| `menus/get-menus.md` | `GET /api/menus` | List own menus as summaries, filtered by an inclusive date range, plus status (authenticated) |
| `menus/post-menus.md` | `POST /api/menus` | Create a drafted weekly menu with its 7 days of meals (authenticated) |
| `menus/get-menus-details.md` | `GET /api/menus/details` | Read own meal plan over a date range — every menu with its days, meals and embedded dishes (authenticated) |
| `menus/patch-menus.md` | `PATCH /api/menus/{menuId}` | Update an owned drafted or scheduled menu's meals and preferences (authenticated) |
| `menus/post-menu-status.md` | `POST /api/menus/{menuId}/status` | Transition an owned menu's status: schedule, cancel, or complete (authenticated) |
| `payments/get-payments.md` | `GET /api/payments` | List own payment history, newest first, with subscription and plan context (authenticated) |
| `payments/post-checkout.md` | `POST /api/payments/checkout` | Start (or resume) a VNPay checkout for an AI plan (authenticated) |
| `payments/get-vnpay-ipn.md` | `GET /api/payments/vnpay/ipn` | VNPay payment notification webhook; fulfils a verified payment (public, signed) |
| `payments/get-payments-paymentid.md` | `GET /api/payments/{paymentId}` | Read one of your payments' status from the ledger (authenticated; owner only) |
| `post/post-posts.md` | `POST /api/posts` | Create a post for the authenticated user |
| `post/get-posts.md` | `GET /api/posts` | List the authenticated user's posts |
| `post/get-posts-feed.md` | `GET /api/posts/feed` | Global feed of published posts, newest first (public) |
| `post/get-posts-postid.md` | `GET /api/posts/{postId}` | View a single non-deleted post by id (any status, any author, public) |
| `post/patch-posts-postid.md` | `PATCH /api/posts/{postId}` | Edit a post (owner or Admin) |
| `post/delete-posts-postid.md` | `DELETE /api/posts/{postId}` | Soft-delete a post (owner or Admin) |
| `post/post-posts-postid-comments.md` | `POST /api/posts/{postId}/comments` | Create a top-level comment or nested reply on a published post (authenticated) |
| `post/get-posts-postid-comments.md` | `GET /api/posts/{postId}/comments` | List a post's comments as a paginated nested thread (public) |
| `post/patch-posts-postid-comments-commentid.md` | `PATCH /api/posts/{postId}/comments/{commentId}` | Edit a comment's content, no time limit (owner only) |
| `post/delete-posts-postid-comments-commentid.md` | `DELETE /api/posts/{postId}/comments/{commentId}` | Soft-delete a comment (owner or Admin) |
| `post/post-posts-postid-comments-commentid-report.md` | `POST /api/posts/{postId}/comments/{commentId}/report` | Report a comment, incrementing its report count (authenticated) |
| `post/post-posts-postid-media.md` | `POST /api/posts/{postId}/media` | Replace a post's whole attached-media set (owner or Admin) |
| `post/post-posts-postid-recipes.md` | `POST /api/posts/{postId}/recipes` | Replace a post's whole attached-recipe set (owner or Admin) |
| `post/get-posts-postid-related.md` | `GET /api/posts/{postId}/related` | Semantically similar published posts by embedding cosine similarity, ranked, excludes source (public) |
| `post/patch-posts-postid-visibility.md` | `PATCH /api/posts/{postId}/visibility` | Hide or unhide a post (Admin) |
| `post/put-posts-postid-votes.md` | `PUT /api/posts/{postId}/votes` | Cast, switch, or remove a vote on a post (authenticated) |
| `post/get-posts-postid-votes.md` | `GET /api/posts/{postId}/votes` | Read a post's vote counts and the caller's own vote if identified (public) |
| `profile/put-profile.md` | `PUT /api/profile` | Update user profile |
| `profile/get-profile.md` | `GET /api/profile` | Get own profile (authenticated; auto-creates empty row) |
| `profile/get-profile-userid.md` | `GET /api/profile/{userId}` | Get a member's profile (public) |
| `recipes/post-recipes.md` | `POST /api/recipes` | Create a recipe (dish + ingredients) for the authenticated user |
| `search/get-search.md` | `GET /api/search` | Keyword search across posts, videos, and recipes (public) |
| `subscriptions/get-subscriptions.md` | `GET /api/subscriptions` | List active AI plans with limits and price (public) |
| `subscriptions/get-me.md` | `GET /api/subscriptions/me` | Get own AI subscription: tier, usage, plan, latest payment (authenticated) |
| `subscriptions/post-cancel.md` | `POST /api/subscriptions/me/cancel` | Cancel own AI subscription immediately, cascading scheduled successors (authenticated) |
| `subscriptions/get-history.md` | `GET /api/subscriptions/me/history` | Paginated history of own AI subscription rows, newest first (authenticated) |
| `subscriptions/post-purchase-eligibility.md` | `POST /api/subscriptions/me/purchase/eligibility` | Check whether a plan purchase/extension is allowed; read-only (authenticated) |
| `post/get-users-userid-posts.md` | `GET /api/users/{userId}/posts` | List a member's posts (public: published only; owner/Admin: all) |
| `mcp/post-mcp.md` | In-process `@Tool` methods (no HTTP endpoint) | Five menu tools — get_menu, get_meal_plan, create_menu, update_menu, schedule_menu — served by the backend's own tool layer for the AI chat (ADR-013) |
